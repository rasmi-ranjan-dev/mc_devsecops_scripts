#!/usr/bin/env python3
import yaml
import subprocess
import os
import argparse
import sys
import json
from datetime import datetime, timedelta, timezone
from urllib import request



#WEBHOOK_URL = "http://172.18.228.57:8081/generic-webhook-trigger/invoke?token=SAST_SCAN"
def load_yaml(file):
    with open(file, 'r') as f:
        return yaml.safe_load(f)

'''
def run_command(cmd):
    print("Executing:", " ".join(cmd))
    subprocess.run(cmd, check=True)
'''

def run_command(cmd):
    # Non-interactive sudo; requires NOPASSWD in sudoers for 'java'
    sudo_cmd = ["sudo", "-H", "-n"] + cmd
    print("Executing:", " ".join(sudo_cmd))
    subprocess.run(sudo_cmd, check=True)

def send_webhook(final_web_url,payload_dict):
    if not final_web_url:
        raise ValueError("final_web_url is empty or None. Ensure --final_web_url is provided.")

    data = json.dumps(payload_dict).encode('utf-8')
    headers = {'Content-Type': 'application/json'}
    req = request.Request(final_web_url, data=data, headers=headers, method='POST')

    print(f"POST {final_web_url}\nPayload: {json.dumps(payload_dict, indent=2)}")
    with request.urlopen(req) as resp:
        body = resp.read().decode('utf-8', errors='replace')
        print(f"Webhook response status: {resp.status}")
        print(f"Webhook response body:\n{body}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    # Keep the SAME flag names/aliases as run_dependency_check.py
    parser.add_argument("--projectname", "--project_name", dest="projectname", required=True)
    parser.add_argument("--scanpath", "--scan_path", dest="scanpath", required=True)
    parser.add_argument("--outputfolder", "--output_folder", dest="outputfolder", required=True)
    parser.add_argument("--web_url", "--WEB_URL", dest="web_url", required=True)
    parser.add_argument("--scantype", default="OSS")
    parser.add_argument("--webhook_token", "--WEBHOOK_TOKEN", dest="webhook_token")
    parser.add_argument("--oss_config_path","--OSS_CONFIG_PATH", dest="oss_cofing_path",required=True)
    parser.add_argument("--remote_dir","--REMOTE_DIR", dest="remote_dir", required=True)

    args = parser.parse_args()

    # Load tool configuration
    toolcfg = load_yaml(args.oss_config_path)
    remote_dir=args.remote_dir.strip()

    # Change directory before resolving JAR
    os.chdir("/u01/Homedir/palamida/code-insight-agent-sdk-generic-plugin/generic-plugin-binary")
    ROOT_PATH = "/u01/Homedir/palamida/plugin_run"
    print(os.getcwd())


    generic_jar = subprocess.getoutput("find . -type f -name 'codeinsight-generic*.jar'").split("\n")[0]
    #generic_jar = "/FNCI-V7-2024R2/Palamida_home/tomcat/webapps/code-insight-agent-sdk-generic-plugin/generic-plugin-binary/codeinsight-generic-3.1.20.jar"


    # ---- Read required connectivity/settings from YAML ----
    # Note: Using your exact keys provided
    server   = toolcfg.get('SERVER', '').strip()
    host     = toolcfg.get('PALAMIDA_URL', '').strip()
    alias    = toolcfg.get('ALIAS', 'Scanner-core').strip()
    scandirs = toolcfg.get('SCANDIR', '.').strip()
    token    = toolcfg.get('TOKEN', '.').strip()
    #generic_jar= toolcfg.get('GENERIC_JAR', '.').strip()
    scandirs =scandirs+remote_dir

    # Java system properties
    agent_reset = 'true' if toolcfg.get('agent_reset', False) else 'false'
    agent_log_level = toolcfg.get('agent_log_level', 'error')


    cmd = [
        "java",
        f"-Dflx.agent.reset={agent_reset}",
        f"-Dflx.agent.logLevel={agent_log_level}",
        "-jar", generic_jar,
        "-server", server,
        "-token", f"Bearer {token}",
        "-proj", args.projectname,
        "-root", os.getcwd(),
        "-scandirs", scandirs,
        "-alias", alias,
        "-host", host
    ]
    #status = "FAILED"
    # Execute
    run_command(cmd)
    print("OSS scan completed.")




    payload = {
        "USERNAME": "admin",
        "project" :args.projectname ,
        "SCAN_TYPE": args.scantype,
        "status" : "SUCCESS"

    }
    final_web_url = args.web_url
    if (args.scantype or "").strip().upper() == "OSS":
        token = (args.webhook_token or "").strip()
        if not token:
            raise ValueError("WEBHOOK_TOKEN is required for OSS scan")


        #final_web_url = final_web_url + "oss_scan_m"
        final_web_url = final_web_url + token

    #payload = toolcfg.get('payload', {})
    send_webhook(final_web_url,payload)
