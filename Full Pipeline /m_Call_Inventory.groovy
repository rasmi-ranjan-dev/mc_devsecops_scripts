/////////////////////////////

def priority_count
def total_items_found

/////////////////////////////

def palamida_url = "http://172.18.228.108:8888"
def flexnet_auth_code = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJTd2FwbmlsIiwidXNlcklkIjo0LCJpYXQiOjE3NzEyMzM1NTN9.zrim2FCPiwCdDKurVhPRk6R07GzJu93msva6lhsmeVT5Eu1TLuB7YzJf9Uo1NaxXtwRxFJn5gGYZ_2_1bWG4Jg"


                sh """
                    curl -s -X GET "$palamida_url/codeinsight/api/projects/${params.REMOTE_DIR}/inventorySummary?vulnerabilitySummary=false&cvssVersion=V3&published=PUBLISHED&offset=1&limit=10000" \
                    -H "accept: application/json" \
                    -H "Authorization: Bearer $flexnet_auth_code" > response.json
                """
     
                // Extract and parse priorities
                priority_count = sh(script: "jq -r '.data[].priority' response.json | sort | uniq -c | awk '{print \$1, \$2}'", returnStdout: true).trim()
                echo "Raw jq output:\n${priority_count}"
     
                // Count total items
                def totalItems = sh(script: "jq -r '.data | length' response.json", returnStdout: true).trim()
                echo "Total Items: ${totalItems}"
     
                // Create a priority map dynamically
                def priorityMap = [:]
                priority_count.split("\n").each { line ->
                    def parts = line.trim().split("\\s+", 2)
                    if (parts.length == 2) {
                        def priority = parts[1].trim()
                        def count = parts[0] as int
                        priorityMap[priority] = count
                    }
                }
     
                // Define priority display order
                def priorityOrder = ["High", "Medium", "Low"]
                def severityMap = ["High": "Priority 1 Items", "Medium": "Priority 2 Items", "Low": "Priority 3 Items"]
     
                // Build defects list and determine overall status
                def defects = []
                def hasHighPriority = false
     
                priorityOrder.each { key ->
                    def count = priorityMap.get(key, 0)
                    def status = (key == "High" && count > 0) ? "red" : "green"
                    if (status == "red") hasHighPriority = true
     
                    defects << [
                        severity: severityMap[key],
                        exceptedValue: key == "High" ? 0 : ">=0",
                        count: count,
                        reportStatus: status
                    ]
                }
     
                def overallStatus = hasHighPriority ? "red" : "green"
     
                def outputJson = [
                    output: [
                        defects: defects,
                        status: overallStatus
                    ],
                    status: "success"
                ]
     
                def jsonOutput = groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(outputJson))
                echo jsonOutput
     
                writeFile(file: 'inventory_summary.json', text: jsonOutput)
                echo "JSON file saved as inventory_summary.json in the workspace"

