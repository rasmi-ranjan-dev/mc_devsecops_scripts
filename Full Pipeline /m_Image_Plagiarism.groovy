sh "cp /var/jenkins_home/Softwares/ipsafe/image_verification/sample.sh ."
sh "cp /var/jenkins_home/Softwares/ipsafe/image_verification/runImageVerifyUtility.sh ."
sh "cp /var/jenkins_home/Softwares/ipsafe/image_verification/ImageBulkUpload.sh ."
sh "whoami"
sh "mkdir -p ${params.PROJECT}_Images"
sh "mkdir -p ${params.PROJECT}_Reports"

def destinationPath = sh(returnStdout: true, script: "realpath ${params.PROJECT}_Images").trim()
echo "$destinationPath"
//sh "./runImageVerifyUtility.sh ${params.JENKINS_MOUNT}/${params.PROJECT}/${params.VERSION}/${params.FOLDER} ${destinationPath}"
// below run to upload images to db
                    
sh "./ImageBulkUpload.sh ${params.JENKINS_MOUNT}/${params.PROJECT}/${params.VERSION}/${params.FOLDER} ${destinationPath} ${params.TIPAR_ID} " 
+ '"Source Code"'
sh "mv copyRightsVerification.xlsx ${params.PROJECT}_Reports"
archiveArtifacts allowEmptyArchive: true, artifacts: "${params.PROJECT}_Reports/copyRightsVerification.xlsx", onlyIfSuccessful: true

//RAG.json will be generateed
//Autentatication
def buildDate = sh(returnStdout: true, script: 'date').trim()
def apiUrl = "${inputApiBaseURL}/api/v1/authentication/auth"
//def requestBody = '{"user_name":"'+"${userName}"+'","password":"'+"${pswd}"+'","accpetedMsgId":null}'
def requestBody = '{"user_name":"' + "cdpadmin" + '","password":"' + "Password@1" 
+ '","accpetedMsgId":null}'
def apiKey = 'c2VjcmV0'
def contentType = 'application/json'
def headerString = 'api-key: c2VjcmV0'
// def curlCommand = "curl -X POST -H 'api-key: c2VjcmV0' -H 'Content-Type: application/json' -d '${requestBody}' ${apiUrl}"
def curlCommand = "curl -X POST -H 'Referer: http://localhost:4200' -H 'User-Agent: Mozilla/5.0' -H 'api-key: c2VjcmV0' -H 'Content-Type: application/json' -d '${requestBody}' ${apiUrl}"            

def response = sh(script: curlCommand, returnStdout: true).trim()
println("auth API response >> " + response)
def startIndex = response.indexOf("token")+8
def endIndex = response.indexOf("tenant")-3
authToken = response.substring(startIndex, endIndex)
println(authToken)
                    
//// pushing to Dashboard
def apiUrl1 = "${inputApiBaseURL}/api/v1/cdpinsights/ipsafe/pipeline/utility"
callInsightsAPI("RAG.json", apiUrl1, buildDate, authToken, "Image Plagiarism", params.PIPELINE)




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

