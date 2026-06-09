#!/bin/bash

# Script de copia de seguridad de bases de datos MySQL del contenedor db
# Autor: GM2 Backup Script
# Fecha: $(date)

# Configuración
CONTAINER_NAME="mysql"
BACKUP_DIR="./backups"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
MYSQL_USER="root"
MYSQL_PASSWORD="widowmaker"

# Crear directorio de backups si no existe
mkdir -p "$BACKUP_DIR"

echo "=========================================="
echo "Iniciando copia de seguridad de bases de datos"
echo "Fecha: $(date)"
echo "Contenedor: $CONTAINER_NAME"
echo "=========================================="

# Obtener lista de bases de datos del contenedor
echo "Obteniendo lista de bases de datos..."
DATABASES=$(docker exec $CONTAINER_NAME mysql -u$MYSQL_USER -p$MYSQL_PASSWORD -e "SHOW DATABASES;" | grep -v Database | grep -v information_schema | grep -v performance_schema | grep -v mysql | grep -v sys)

# Crear backup completo del contenedor (opcional)
echo "Creando backup completo del contenedor..."
docker exec $CONTAINER_NAME mysqldump -u$MYSQL_USER -p$MYSQL_PASSWORD --all-databases > "$BACKUP_DIR/full_backup_$TIMESTAMP.sql"
echo "Backup completo guardado en: $BACKUP_DIR/full_backup_$TIMESTAMP.sql"

# Crear backups individuales de cada base de datos
echo "Creando backups individuales..."
for DB in $DATABASES; do
    echo "Backup de base de datos: $DB"
    docker exec $CONTAINER_NAME mysqldump -u$MYSQL_USER -p$MYSQL_PASSWORD "$DB" > "$BACKUP_DIR/${DB}_$TIMESTAMP.sql"
    echo "Backup de $DB guardado en: $BACKUP_DIR/${DB}_$TIMESTAMP.sql"
done

# Comprimir los archivos de backup
echo "Comprimiendo archivos de backup..."
cd "$BACKUP_DIR"
tar -czf "backup_databases_$TIMESTAMP.tar.gz" *.sql
cd ..

# Eliminar archivos SQL sin comprimir (opcional)
# rm "$BACKUP_DIR"/*.sql

# Listar archivos creados
echo "=========================================="
echo "Backups creados:"
ls -lh "$BACKUP_DIR/backup_databases_$TIMESTAMP.tar.gz"
echo "=========================================="

# Limpiar backups antiguos (mantener solo los últimos 7 días)
echo "Limpiando backups antiguos..."
find "$BACKUP_DIR" -name "backup_databases_*.tar.gz" -mtime +7 -delete
echo "Limpieza completada"

echo "=========================================="
echo "Copia de seguridad completada exitosamente"
echo "Fecha: $(date)"
echo "=========================================="
