-def utility
def buildDate
def authToken


echo 'Running OSS Utility...'
buildDate = sh(returnStdout: true, script: 'date').trim()
echo "Build Date: ${buildDate}"
echo "i am going to execute the script"
def apiUrl = "${params.inputApiBaseURL}/api/v1/authentication/auth"
def requestBody = '{"user_name":"' + "cdpadmin" + '","password":"' + "Password@1" 
+ '","accpetedMsgId":null}'
def apiKey = 'c2VjcmV0'
def contentType = 'application/json'
// sh "export http_proxy = http://proxy.tcs.com:8080" 
// sh "export https_proxy = https://proxy.tcs.com:8080"
def headerString = 'api-key: c2VjcmV0'
def curlCommand = "unset http_proxy && unset https_proxy && curl -X  POST -H 'api-key: c2VjcmV0' -H 'Content-Type: application/json' -H 'Referer: http://localhost:4200' -H 'User-Agent: Mozilla/5.0' -d '${requestBody}' ${apiUrl}  "
def response = sh(script: curlCommand, returnStdout: true).trim()
println("auth API response >> " + response)
def startIndex = response.indexOf("token")+8
def endIndex = response.indexOf("tenant")-3
authToken = response.substring(startIndex, endIndex)
println(authToken)
sh "echo ${authToken} > authToken.txt"
//sh "echo ${tipar_id}"
sh 'pwd'
def directory = new File("all_folders")
if (fileExists("$directory")) 
{
    println('The directory exists.')
    echo  "$directory"
}
else  {
    sh "mkdir -p all_folders"
    sh "cd all_folders && mkdir -p OSS "
}

sh '''cd all_folders/OSS && rm -rf *'''
def compList = "${params.COMPONENT_LIST}"
                
                
echo "${params.COMPONENT_LIST}"
//echo "${compList}"
sh "hostname -i"
compList.split("\\r?\\n").each {
    comp -> println "Param: ${comp}"
    runOSSUtility(comp)
}



sh "test -f aggregator.sh || cp /var/jenkins_home/Softwares/Utilities/aggregator.sh ."
sh "./aggregator.sh OSS"
                        
archiveArtifacts artifacts: "OSS_aggregatedRAG.json", followSymlinks: false, onlyIfSuccessful: true
                        
//sh "cp OSS_aggregatedRAG.json /var/jenkins_home/jobs/${params.PIPELINE}/workspace/all_folders/OSS/${params.TIPAR_ID}"
                        
def apiUrl1 = "${params.inputApiBaseURL}/api/v1/cdpinsights/ipsafe/pipeline/utility"
        
callInsightsAPI("${env.WORKSPACE}/OSS_aggregatedRAG.json", apiUrl1, buildDate, authToken, "3rd Party Tech", params.PIPELINE)



def runOSSUtility(String component) {
    sh "mkdir -p all_folders/OSS/" + params.TIPAR_ID + ""
    def home_path = sh(returnStdout: true, script: 'echo $(pwd)').trim()
    echo "${home_path}"
    dir("./OSS/" + component) {
        sh "ls -l /shared_files/" + PROJECT + "/" + VERSION + "/"
        sh "ls -l /shared_files/" + PROJECT + "/" + VERSION + "/" + component
        sh "cp -r /shared_files/" + PROJECT + "/" + VERSION + "/" + component + " ."
        sh "cp /var/jenkins_home/Softwares/OSS/IpsafeKeyStore.jks ."
        sh "cp /var/jenkins_home/Softwares/OSS/ossVerification.jar ."
        sh "cp /var/jenkins_home/Softwares/OSS/key.jks ."
        sh '''java -jar ossVerification.jar -inputPaths "$(pwd)"'''
        sh "mkdir -p " + home_path + "/all_folders/OSS/" + params.TIPAR_ID + component
        sh "cp OSS_.xlsx " + home_path + "/all_folders/OSS/" + params.TIPAR_ID + component 
            + "/" + component.replaceAll('/', '_') + "_OSS.xlsx"
        sh "cp OSS__RAG.json " + home_path + "/all_folders/OSS/" + params.TIPAR_ID 
            + component + "/" + component.replaceAll('/', '_') + "_RAG.json "
        sh "rm -f JileDSOCEGKeyStore.jks"
        sh "rm -f ossVerification.jar"
    }
    sh "rm -rf ./OSS"
}


def callInsightsAPI(String outputPath, String apiUrl, String buildDate, String authToken, String stageName, String pipeline) {
    echo "callInsightsAPI method for pipeline ${pipeline}"
    echo "Calling ResponseTIME INSIGHTS API"
    def jsonOutput = readFile(outputPath)
    echo "${jsonOutput}"
    jsonOutput = jsonOutput.trim();
    if (!(jsonOutput.startsWith('{'))) {
        jsonOutput = "{${jsonOutput}}"
    }
    // def buildNum = sh(returnStdout: true, script: "cat /var/jenkins_home/jobs/${JOB_BASE_NAME}/builds/${BUILD_NUMBER}/log | head -1 | awk '{print \$NF}' | tr -d '\\n'")
    //   echo "${buildNum}"
    def buildNum1 = params.BUILD_NUM
    // def buildNum1=894
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

