/////////////////////////////

def priority_count
def total_items_found

/////////////////////////////

def palamida_url = "http://172.18.228.177:8888"
def flexnet_auth_token = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJwYWxhbWlkYSIsInVzZXJJZCI6MywiaWF0IjoxNzM0MDIzMjc0fQ.hyTpnABV54igsluUgja4G2G0ze_TC1P_mwRrcJXJySv02vKkpJJJokGvb2U0L0OB8ewIMWuwU_cUYnfTDAkWKQ"
def taskId
def extractedValue


               

sh """
                        curl -s -X GET "$palamida_url/codeinsight/api/project/inventory/${params.REMOTE_DIR}?skipVulnerabilities=true&published=true&size=10000&page=1&includeFiles=false&includeCopyrights=false" \
                        -H "accept: application/json" \
                        -H "Authorization: Bearer $flexnet_auth_token"
                    """
echo "done"
     
sh """
                        curl -s -X GET "$palamida_url/codeinsight/api/projects/${params.REMOTE_DIR}/inventorySummary?vulnerabilitySummary=false&cvssVersion=V3&published=PUBLISHED&offset=1&limit=10000" \
                        -H "accept: application/json" \
                        -H "Authorization: Bearer $flexnet_auth_token" > response.json
                    """
                    
//////////////
priority_count = sh(script: "jq -r '.data[].priority' response.json | sort | uniq -c | awk '{print \$1, \$2}'", returnStdout: true).trim()
echo "Raw jq output:\n${priority_count}"
/////////////
                    
total_items_found = sh(script: "jq -r '.data[].itemNumber' response.json", returnStdout: true).trim()
def lines = total_items_found.split("\n")
def lastValue = lines[-1].trim()
                    
echo "Total Items found: ${lastValue}"
                    
                    
// Get total items count
def totalItems = sh(script: "jq -r '.data | length' response.json", returnStdout: true).trim()
                    
echo "${totalItems} ......."
                     
def priorityMap = ["Low": 0, "Medium": 0, "High": 0]
priority_count.split("\n").each {
    line -> def parts = line.trim().split("\\s+", 2) // Ensures we split into count & priority
    echo "Processing line: ${parts}"
    if (parts.length == 2) {
        def priorityName = parts[1].trim() // Ensure no leading/trailing spaces
        priorityMap[priorityName] = parts[0] as int
    }
}
                    
echo "${priorityMap}"
                    
def outputJson = [
    "output": [
        "defects": [
            [
                "severity": "Priority 1 Items",
                "exceptedValue": 0,
                "count": priorityMap["High"],
                "reportStatus": priorityMap["High"] > 0 ? "red": "green"
            ],
[
                "severity": "Priority 2 Items",
                "exceptedValue": ">=0",
                "count": priorityMap["Medium"],
                "reportStatus": priorityMap["Medium"] >= 0 ? "green": "green"
            ],
[
                "severity": "Priority 3 Items",
                "exceptedValue": ">=0",
                "count": priorityMap["Low"],
                "reportStatus": priorityMap["Low"] >= 0 ? "green": "green"
            ]
        ],
        "status": "green"
    ],
    "status": "success"
]
                     
def jsonOutput = groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(outputJson))
echo jsonOutput
                     
// Create the formatted output
// def reportContent = """\
// Inventory Summary
// Total Items Found\t${totalItems}
// Priority 1 Items\t\t${priorityMap["High"]}
// Priority 2 Items\t\t${priorityMap["Medium"]}
// Priority 3 Items\t\t${priorityMap["Low"]}
// """
                     
// // Write to a file
writeFile(file: 'inventory_summary.json', text: jsonOutput)
                    
echo "JSON file saved as priority_report.json in the workspace"
                     
// // Print for verification
// echo reportContent


def apiUrl = "${params.inputApiBaseURL}/api/v1/authentication/auth"
def requestBody = '{"user_name":"' + "cdpadmin" + '","password":"' + "Password@1" 
+ '","accpetedMsgId":null}'
def apiKey = 'c2VjcmV0'
def contentType = 'application/json'
                 
def headerString = 'api-key: c2VjcmV0'
def curlCommand = "unset http_proxy && unset https_proxy && curl -X  POST -H 'api-key: c2VjcmV0' -H 'Content-Type: application/json' -H 'Referer: http://localhost:4200' -H 'User-Agent: Mozilla/5.0' -d '${requestBody}' ${apiUrl}  "
def response = sh(script: curlCommand, returnStdout: true).trim()
println("auth API response >> " + response)
def startIndex = response.indexOf("token")+8
def endIndex = response.indexOf("tenant")-3
authToken = response.substring(startIndex, endIndex)
println(authToken)
sh "echo ${authToken} > authToken.txt"
buildDate = sh(returnStdout: true, script: 'date').trim()
echo "Build Date: ${buildDate}"
                      	   

def apiUrl1 = "${params.inputApiBaseURL}/api/v1/cdpinsights/ipsafe/pipeline/utility"
callInsightsAPI("inventory_summary.json", apiUrl1, buildDate, authToken, "OSS", params.PIPELINE)
                
def callInsightsAPI(String outputPath, String apiUrl, String buildDate, String authToken, String stageName, String pipeline) {
    echo "callInsightsAPI method for pipeline ${pipeline}"
    echo "Calling ResponseTIME INSIGHTS API"
    def jsonOutput = readFile(outputPath)
    echo "${jsonOutput}"
    jsonOutput = jsonOutput.trim();
    if (!(jsonOutput.startsWith('{'))) {
        jsonOutput = "{${jsonOutput}}"
    }
    def buildNum = params.BUILD_NUM
    //  def buildNum = sh(returnStdout: true, script: "cat /var/jenkins_home/jobs/${JOB_BASE_NAME}/builds/${BUILD_NUMBER}/log | head -1 | awk '{print \$NF}' | tr -d '\\n'")
    // buildNum=sh(script: buildNum, returnStdout: true).trim()
    echo "${buildNum}"
    echo "${pipeline}"
    jsonOutput = jsonOutput[0.. < jsonOutput.size() -1] + ',"pipeline":\"' + "${pipeline}" 
        + '\","stage":\"' + "${stageName}" + '\","buildNumber":\"' + "${BUILD_NUM}" 
        + '\","buildDate":\"' + "${buildDate}" + '\"}'
    echo "${jsonOutput}"
    echo "stage utility"
    def curlCommand = "unset http_proxy && unset https_proxy && curl -X POST -H 'api-key: c2VjcmV0' -H 'Content-Type: application/json' -H 'auth-token: ${authToken}' -H 'Referer: http://localhost:4200' -H 'User-Agent: Mozilla/5.0' -d '${jsonOutput}' ${apiUrl}"
    def response1 = sh(script: curlCommand, returnStdout: true).trim()
    println("response1 >>> " + response1)
}

