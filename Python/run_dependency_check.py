
#!/usr/bin/env python3
import yaml
import subprocess
import os
import argparse
import json
from datetime import datetime, timedelta, timezone
from urllib import request


# WEBHOOK_URL = "http://172.18.228.57:8081/generic-webhook-trigger/invoke?token=SAST_SCAN"

def load_yaml(file):
    with open(file, 'r') as f:
        return yaml.safe_load(f)

def run_command(cmd):
    print("Executing:", " ".join(cmd))
    subprocess.run(cmd, check=True)


def ist_now_str():
    ist = timezone(timedelta(hours=5, minutes=30))
    now_ist = datetime.now(tz=ist)
    return now_ist.strftime('%Y-%m-%d %I:%M:%S.%f %p')[:-3]

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

    print("Hello , I'm befor argparse ",flush=True)
    parser = argparse.ArgumentParser()
    parser.add_argument("--projectname", "--project_name", dest="projectname", required=True)
    parser.add_argument("--scanpath", "--scan_path", dest="scanpath", required=True)
    parser.add_argument("--outputfolder", "--output_folder", dest="outputfolder", required=True)
    parser.add_argument("--web_url", "--WEB_URL", dest="web_url", required=True)
    parser.add_argument("--username", default="admin")
    parser.add_argument("--scantype", default="CVC")
    parser.add_argument("--buildId", "--BUILD_ID", dest="buildId")
    parser.add_argument("--webhook_token", "--WEBHOOK_TOKEN", dest="webhook_token", required=True)
    parser.add_argument("--cvc_config_path", "--cvc_config_path", dest="cvc_config_path", required=True)

    args = parser.parse_args()
    print("Project name: ", args.projectname, flush=True )

    toolcfg = load_yaml(args.cvc_config_path)
    print(f"Webhook token present? {'yes' if args.webhook_token else 'no'}")
    # report_path = os.path.join(args.outputfolder, toolcfg['report'])
    report_path = os.path.join(args.outputfolder)
    suppression_path = os.path.join(args.outputfolder, toolcfg.get('suppressionfile', ''))

    cmd = [
        toolcfg['dependency_check_bin'],
        "--scan", args.scanpath,
        "--format", toolcfg['report_format'],
        "--out", report_path
    ]

    # if suppression_path.strip():
    #     cmd += ['--suppression', suppression_path]

    if toolcfg.get('data_directory'):
        cmd += ['--data', toolcfg['data_directory']]
    if toolcfg.get('property_file'):
        cmd += ['--propertyfile', toolcfg['property_file']]
    if toolcfg.get('log_file'):
        cmd += ['--log', toolcfg['log_file']]

    if toolcfg.get('noupdate'): cmd.append('--noupdate')
    if toolcfg.get('disableAssembly'): cmd.append('--disableAssembly')
    if toolcfg.get('disableOssIndex'): cmd.append('--disableOssIndex')
    if toolcfg.get('disableCentral'): cmd.append('--disableCentral')
    if toolcfg.get('disableRetireJS'): cmd.append('--disableRetireJS')
    if toolcfg.get('disableNodeAudit'): cmd.append('--disableNodeAudit')

    # run_command(cmd)
    # print("Dependency-Check scan completed.")
    #Added default value of status
    status = "FAILED"
    try:
        run_command(cmd)
        print("Dependency-Check scan completed.")
        status = "SUCCESS"
    except subprocess.CalledProcessError:
        print("Dependency-Check scan failed.")
        status = "FAILED"



# creating the webhook payload
    payload = {
        "USERNAME": "admin",
        "SCAN_TYPE": args.scantype,
        "reportUrl": report_path,
        "status" : status ,
        "buildID" : args.buildId,
        "project": args.projectname,
        "timestamp": ist_now_str()
    }
    print("Build ID is:", args.buildId)
    final_web_url = args.web_url
    if (args.scantype or "").strip().upper() == "CVC":

        token = (args.webhook_token or "").strip()
        if not token:
            raise ValueError("WEBHOOK_TOKEN is required for CVC scan")

        #final_web_url = final_web_url + "dso_generic_receiver_token"
        final_web_url = final_web_url + token
        #cvc_scan_m

    # Send exactly the YAML payload, without overrides
   # payload = toolcfg.get('payload', {})
    #send_webhook(args.web_url,payload)
    send_webhook(final_web_url,payload)
