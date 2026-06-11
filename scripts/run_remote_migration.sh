#!/usr/bin/env bash
set -euo pipefail

# Remote connection details
SSH_USER="server"
SSH_HOST="equibiomedic.co"
SSH_PORT="5434"
REMOTE_DIR="/media/backup/server/GM2"
SQL_FILE="$REMOTE_DIR/backend/src/main/resources/db/migration/V2__add_datos_metrologicos.sql"
ALTER_SQL="ALTER TABLE products ADD COLUMN datos_metrologicos_producto TEXT;"
MYSQL_ROOT_PASS="eqbm2022"

ssh -p "$SSH_PORT" "$SSH_USER@$SSH_HOST" bash -s <<'REMOTE_EOF'
set -euo pipefail

REMOTE_DIR="/media/backup/server/GM2"
SQL_FILE="$REMOTE_DIR/backend/src/main/resources/db/migration/V2__add_datos_metrologicos.sql"
ALTER_SQL="ALTER TABLE products ADD COLUMN datos_metrologicos_producto TEXT;"
MYSQL_ROOT_PASS="eqbm2022"

echo "Entering $REMOTE_DIR"
cd "$REMOTE_DIR" || { echo "Remote directory not found: $REMOTE_DIR"; exit 1; }

# Try to find MySQL containers by image
MYSQL_CONTAINERS="$(docker ps --filter "ancestor=mysql" --format "{{.Names}}")"
if [ -z "$MYSQL_CONTAINERS" ]; then
  MYSQL_CONTAINERS="$(docker ps --filter "ancestor=mariadb" --format "{{.Names}}")"
fi

if [ -z "$MYSQL_CONTAINERS" ]; then
  echo "No mysql/mariadb containers found with image filter. Attempting to inspect docker-compose services."
  # Try infra compose
  if [ -f docker-compose.infra.yml ]; then
    IDS="$(docker-compose -f docker-compose.infra.yml ps -q 2>/dev/null || true)"
    for id in $IDS; do
      NAME="$(docker inspect --format '{{.Name}} {{range .Config.Image}}{{.}}{{end}}' $id 2>/dev/null | awk '{print $1}' | sed 's#/##')"
      IMG="$(docker inspect --format '{{range .Config.Image}}{{.}}{{end}}' $id 2>/dev/null)"
      if echo "$IMG" | grep -iq "mysql\|mariadb"; then
        MYSQL_CONTAINERS="$MYSQL_CONTAINERS $NAME"
      fi
    done
  fi
  # Try develop compose
  if [ -f docker-compose.develop.yml ]; then
    IDS="$(docker-compose -f docker-compose.develop.yml ps -q 2>/dev/null || true)"
    for id in $IDS; do
      NAME="$(docker inspect --format '{{.Name}} {{range .Config.Image}}{{.}}{{end}}' $id 2>/dev/null | awk '{print $1}' | sed 's#/##')"
      IMG="$(docker inspect --format '{{range .Config.Image}}{{.}}{{end}}' $id 2>/dev/null)"
      if echo "$IMG" | grep -iq "mysql\|mariadb"; then
        MYSQL_CONTAINERS="$MYSQL_CONTAINERS $NAME"
      fi
    done
  fi
fi

# Normalize whitespace
MYSQL_CONTAINERS="$(echo $MYSQL_CONTAINERS | xargs)"

if [ -z "$MYSQL_CONTAINERS" ]; then
  echo "No MySQL containers found. Exiting.";
  exit 1;
fi

echo "MySQL containers to migrate: $MYSQL_CONTAINERS"

for c in $MYSQL_CONTAINERS; do
  echo "-- Processing container: $c"
  if [ -f "$SQL_FILE" ]; then
    echo "Copying SQL file into container and executing..."
    docker cp "$SQL_FILE" "$c":/tmp/V2__add_datos_metrologicos.sql
    docker exec -i "$c" bash -c "mysql -u root -p\"$MYSQL_ROOT_PASS\" < /tmp/V2__add_datos_metrologicos.sql" || {
      echo "Fallback: running ALTER TABLE directly"
      docker exec -i "$c" bash -c "mysql -u root -p\"$MYSQL_ROOT_PASS\" -e \"$ALTER_SQL\""
    }
    docker exec -i "$c" bash -c "rm -f /tmp/V2__add_datos_metrologicos.sql" || true
  else
    echo "SQL file not found at $SQL_FILE; running ALTER TABLE directly"
    docker exec -i "$c" bash -c "mysql -u root -p\"$MYSQL_ROOT_PASS\" -e \"$ALTER_SQL\""
  fi
done

# Try restart backend services if present
if [ -f docker-compose.infra.yml ]; then
  echo "Restarting backend in infra compose..."
  docker-compose -f docker-compose.infra.yml restart backend || true
fi
if [ -f docker-compose.develop.yml ]; then
  echo "Restarting backend in develop compose..."
  docker-compose -f docker-compose.develop.yml restart backend || true
fi

echo "Migration completed on remote host."
REMOTE_EOF
