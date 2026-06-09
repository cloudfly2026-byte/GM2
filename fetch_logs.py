import paramiko

def check_new_logs():
    try:
        ssh = paramiko.SSHClient()
        ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        ssh.connect('181.205.220.123', port=5434, username='server', password='eqbm2022', timeout=10)

        for container in ['gm2_backend-spring_develop_1', 'gm2_app_develop']:
            print(f"\n========== LOGS FOR {container} ==========")
            stdin, stdout, stderr = ssh.exec_command(f"sudo -S docker logs --tail 50 {container}")
            stdin.write('eqbm2022\n')
            stdin.flush()
            print(stdout.read().decode('utf-8'))
            
        ssh.close()
    except Exception as e:
        print(f"Error: {e}")

if __name__ == '__main__':
    check_new_logs()
