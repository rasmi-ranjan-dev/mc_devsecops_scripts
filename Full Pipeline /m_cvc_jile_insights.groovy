def primaryDest = "/var/jenkins_home/jobs/${PIPELINE}/workspace/all_folders/CVC/${TIPAR_ID}"
def fallBack = "/var/jenkins_home/jobs/${PIPELINE}/workspace"
def paths="$JENKINS_NFS_MOUNT/${PROJECT}/CVC"
def Grype_Home="/var/jenkins_home/Softwares/Grype"
def excel_path = "${env.WORKSPACE}/${PROJECT}/dependency-check-report.xlsx"
def csv_path = "${env.WORKSPACE}/${PROJECT}/report_grype.csv"
def dest =''
def paths2="$JENKINS_NFS_MOUNT/${PROJECT}/${VERSION}/${FOLDER}"
def jars_path = "$JENKINS_NFS_MOUNT/${PROJECT}/${VERSION}/jarstmp"
def root_path = "$JENKINS_NFS_MOUNT/${PROJECT}/${VERSION}"


//Check if a directory with project name exists in current workspace
                     def directory = new File("${params.PROJECT}")
          
                   if(fileExists("$directory")) {
                         println('The directory exists.')
                          echo "$directory"
                    } 
                    else {
                sh "mkdir -p "+ params.PROJECT
                sh "chmod +x "+ params.PROJECT
                          }
                              
			    markBuild pipelineType: 'PT-CD'
			    println "${params.BUILD_NUM}"
                def TIPAR_ID="${params.TIPAR_ID}"
                sh "echo parent tiparid  is $TIPAR_ID"



//Syft scan
                if("${params.PROJECT}" == "FEE") {
                echo "COPY STarting...."
                sh "sudo cp -r /shared_files/FEE/JarsForCVC/* /shared_files/FEE/CVC/jarsfoldernew2"
                echo "COPY Completed...."
                } 
            
                
                 sh "sudo sh $WORKSPACE/Shell_scripts/cvc.sh $JENKINS_NFS_MOUNT ${PROJECT}  ${params.VERSION} ${params.FOLDER}"


                dir('/var/jenkins_home/Softwares/Syft'){
                echo 'Running Syft Scan on Source'
                //Source must be directory or image here
 sh "export http_proxy=http://172.17.0.11:8080 && export https_proxy=http://172.17.0.11:8080 && ./syft ${paths} -o cyclonedx-json > ${WORKSPACE}/${PROJECT}/syft_cyclonedx.json"            
                    archiveArtifacts artifacts: 'syft_cyclonedx.json', followSymlinks: false
                }

            //Grype Scan started

            dir("${Grype_Home}"){
                echo 'Running Grype Scan on Source'
                sh "export GRYPE_DB_AUTO_UPDATE=false && export GRYPE_DB_ROOT=${Grype_Home} && export http_proxy=http://172.17.0.11:8080 && export https_proxy=http://172.17.0.11:8080 && ./grype ${paths2} -o template -t csv.tmpl > ${WORKSPACE}/${PROJECT}/report_grype.csv"
                
                sh "export GRYPE_DB_AUTO_UPDATE=false && export GRYPE_DB_ROOT=${Grype_Home} && export http_proxy=http://172.17.0.11:8080 && export https_proxy=http://172.17.0.11:8080 && ./grype ${paths2} -o template -t html.tmpl > ${WORKSPACE}/${PROJECT}/report_grype.html"

                 sh "export GRYPE_DB_AUTO_UPDATE=false && export GRYPE_DB_ROOT=${Grype_Home} && export http_proxy=http://172.17.0.11:8080 && export https_proxy=http://172.17.0.11:8080 && ./grype ${paths2} -o template -t html_cve.tmpl > ${WORKSPACE}/${PROJECT}/report_grype_cve.html"
                 
                sh "export GRYPE_DB_AUTO_UPDATE=false && export GRYPE_DB_ROOT=${Grype_Home} && export http_proxy=http://172.17.0.11:8080 && export https_proxy=http://172.17.0.11:8080 && ./grype ${paths2} -o json > ${WORKSPACE}/${PROJECT}/report_grype.json"
            
                    //archiveArtifacts artifacts: 'report_grype.csv', followSymlinks: false

             }
             archiveArtifacts artifacts: "${PROJECT}/report_grype.csv", followSymlinks: false
             archiveArtifacts artifacts: "${PROJECT}/report_grype.html", followSymlinks: false
             archiveArtifacts artifacts: "${PROJECT}/report_grype_cve.html", followSymlinks: false
             archiveArtifacts artifacts: "${PROJECT}/report_grype.json", followSymlinks: false
             
            buildDate = sh(returnStdout: true,script:'date').trim()
			echo "Build Date: ${buildDate}"
            echo "======= Executing CVC Scan initiation script ======="
                   
           //  nvdApiKey 1cece696-9c08-4cc0-97a1-69635b53573c --project "${PROJECT}" --enableExperimental 
            sh "sudo rm -rf '${jars_path}'"
            sh "sudo mkdir -p '${jars_path}'"
            sh "sudo rsync -avm --include='*jar' --include='*/' --exclude='*' '${root_path}' '${jars_path}'"

            if("${params.PROJECT}" == "FEE") {
                sh """ /var/jenkins_home/Softwares/dependency-check-12.1.0/bin/dependency-check.sh --scan "$JENKINS_NFS_MOUNT/${PROJECT}/CVC/jarsfoldernew2" --format ALL --disableOssIndex true --data "/var/jenkins_home/nvddbupdate/nvddbupdateonly" --out "${PROJECT}" --noupdate --enableExperimental --enableRetired --disableAssembly --propertyfile "/var/jenkins_home/Softwares/dependency-check-12.1.0/dependency-check.properties" --log "/var/jenkins_home/Softwares/dependency-check-12.1.0/cvc.log" """
            } else {
                sh """ /var/jenkins_home/Softwares/dependency-check-12.1.0/bin/dependency-check.sh --scan "${jars_path}" --format ALL --disableOssIndex true --data "/var/jenkins_home/nvddbupdate/nvddbupdateonly" --out "${PROJECT}" --noupdate --enableExperimental --enableRetired --disableAssembly --propertyfile "/var/jenkins_home/Softwares/dependency-check-12.1.0/dependency-check.properties" --log "/var/jenkins_home/Softwares/dependency-check-12.1.0/cvc.log" """
            }
            

		
                   
                    
            def report_path="${PROJECT}/dependency-check-report.xml"
                     echo "finding reports in $report_path"
                      dependencyCheckPublisher pattern: "$report_path"
                      
                     archiveArtifacts allowEmptyArchive: true, artifacts: "${PROJECT}/dependency-check-report.xml", onlyIfSuccessful: true
                      echo "${PROJECT}"
                      
                 
                   def Scanreports="/var/jenkins_home/cvc/workspace/${env.JOB_NAME}/${PROJECT}"
                   def reportOutput="all_folders/CVC/${TIPAR_ID}/Security/CVCAndTechstack/${PROJECT}"
                    
                    sh "echo $FOLDER"
        //Not required as of now 
        
         /*
                    sh '''
                        mkdir -p  /var/jenkins_home/jobs/${env.JOB_NAME}/workspace/${PROJECT}
                        cd /var/jenkins_home/jobs/${env.JOB_NAME}/workspace/${PROJECT}
                       sudo  cp -r  . /var/jenkins_home/cvc/workspace/${env.JOB_NAME}/${PROJECT}
                    '''

                     sh  "java -jar /var/jenkins_home/Softwares/CVC/CVCAndTechstack_v7.1.jar -proxyHost 172.17.0.11 -proxyPort 8080 -cvcXml $Scanreports/dependency-check-report.xml -previousReport ${reportOutput}/CVC_${PROJECT}_PrevReport.xlsx -moduleName ${PROJECT} -outputPath ./${reportOutput} -techStackXlsx ${paths}/${PROJECT}_Techstack.xlsx -metaDataXlsx ${paths}/${PROJECT}_Metadata.xlsx"
                 */

       // sh "echo '================Merge both Dependency Check and CVC reports================'"



                 //Merging both Grype and Dependency Check Reports

                
                  //sh ". /var/jenkins_home/Softwares/ipsafe/image_verification/venv_image_verify/bin/activate && cd ${Grype_Home} && python3 /var/jenkins_home/Softwares/Grype/depcheck-lastest.py --Project ${PROJECT} --inpath ${WORKSPACE}/${PROJECT} --outpath ${WORKSPACE}/${PROJECT} && python3 /var/jenkins_home/Softwares/Grype/report_merger.py ${WORKSPACE}/${PROJECT}/dependency-check-report.xlsx ${WORKSPACE}/${PROJECT}/report_grype.csv" 

       //--previousReport ${WORKSPACE}/${PROJECT}/dependency-check-prevReport.xlsx need to be included in incremental
              
                // archiveArtifacts allowEmptyArchive: true, artifacts: "${PROJECT}/dependency-check-report.xlsx", onlyIfSuccessful: true
                sh "echo '================Merge both Dependency Check and Grype CSV reports================'"



                 //Merging both Grype and Dependency Check CSV Reports

                  sh "sudo cp ${Grype_Home}/*.py ${WORKSPACE}/${PROJECT} && cd ${WORKSPACE}/${PROJECT} &&sudo chmod +x depcheck-lastest.py && sudo chmod +x report_merger.py && sudo chmod +x grype_dc_json_merge.py "
                  
                 /* if("${params.BUILD_TYPE}"=="full"){
                
                //Reads dependency check csv and customizes it to excel, that updated excel n grype csv are passed as inputs to second utility for final merged DC excel 
                sh ". /var/jenkins_home/Softwares/ipsafe/image_verification/venv_image_verify/bin/activate && cd ${WORKSPACE}/${PROJECT} && python3 /var/jenkins_home/Softwares/Grype/depcheck-lastest.py --Project ${PROJECT} --inpath ${WORKSPACE}/${PROJECT} --outpath ${WORKSPACE}/${PROJECT} && python3 /var/jenkins_home/Softwares/Grype/report_merger.py ${WORKSPACE}/${PROJECT}/dependency-check-report.xlsx ${WORKSPACE}/${PROJECT}/report_grype.csv  " 
               // sh "mv ${WORKSPACE}/${PROJECT}/dependency-check-report.xlsx ${WORKSPACE}/${PROJECT}/dependency-check-PrevReport.xlsx" 

                  }*/
                 // else{
                   sh ". /var/jenkins_home/Softwares/ipsafe/image_verification/venv_image_verify/bin/activate && cd ${WORKSPACE}/${PROJECT} && python3 /var/jenkins_home/Softwares/Grype/depcheck-lastest.py --Project ${PROJECT} --inpath ${WORKSPACE}/${PROJECT} --outpath ${WORKSPACE}/${PROJECT} && python3 /var/jenkins_home/Softwares/Grype/report_merger.py ${WORKSPACE}/${PROJECT}/dependency-check-report.xlsx ${WORKSPACE}/${PROJECT}/report_grype.csv  "

                   sh " echo '=============running utility to get techstack and audit data from prev report======='"

               // sh  "java -jar /var/jenkins_home/Softwares/CVC/CVCAndTechstack-v7.7_patch.jar -proxyHost 172.17.0.11 -proxyPort 8080 -cvcXml ${WORKSPACE}/${PROJECT}/dependency-check-report.xlsx -previousReport ${WORKSPACE}/${PROJECT}/dependency-check-PrevReport.xlsx -moduleName ${PROJECT} -outputPath ${WORKSPACE}/${PROJECT}"

                sh " echo '=============running utility to get techstack and audit data from prev report======='"
                   sh "if [ \$(ls -1 ${WORKSPACE}/${PROJECT}/dependency-check-PrevReport.xlsx 2>/dev/null | wc -l) -eq 1 ]; then java -jar /var/jenkins_home/Softwares/CVC/CVCAndTechstack-v8.7.jar -proxyHost 172.17.0.11 -proxyPort 8080 -cvcXml ${WORKSPACE}/${PROJECT}/dependency-check-report.xlsx -previousReport ${WORKSPACE}/${PROJECT}/dependency-check-PrevReport.xlsx -moduleName ${PROJECT} -outputPath ${WORKSPACE}/${PROJECT} -techStackXlsx $JENKINS_NFS_MOUNT/${PROJECT}/${PROJECT}_Techstack.xlsx -metaDataXlsx /var/jenkins_home/Softwares/CVC/CPE_IDs_Feb_metatdata.xlsx; else echo 'file not found'; fi" 


                                 
                   
                   sh "mv ${WORKSPACE}/${PROJECT}/dependency-check-report.xlsx ${WORKSPACE}/${PROJECT}/dependency-check-PrevReport.xlsx"

                

       //--previousReport ${WORKSPACE}/${PROJECT}/dependency-check-prevReport.xlsx need to be included in incremental
       sh " cp ${WORKSPACE}/${PROJECT}/dependency-check-PrevReport.xlsx /var/jenkins_home/jobs/${PIPELINE}/workspace/all_folders/CVC/${params.TIPAR_ID} "

 
                 
                 // }

       //--previousReport ${WORKSPACE}/${PROJECT}/dependency-check-prevReport.xlsx need to be included in incremental
              
                 archiveArtifacts allowEmptyArchive: true, artifacts: "${PROJECT}/dependency-check-PrevReport.xlsx", onlyIfSuccessful: true

     // Push updates to dashboard

     def apiUrl = "${params.inputApiBaseURL}/api/v1/authentication/auth"
                     def requestBody = '{"user_name":"'+"cdpadmin"+'","password":"'+"Password@1"+'","accpetedMsgId":null}'
                     def apiKey = 'c2VjcmV0'
                     def contentType = 'application/json'
                 
                     def headerString = 'api-key: c2VjcmV0'
                     def curlCommand = "unset http_proxy && unset https_proxy && curl -X  POST -H 'api-key: c2VjcmV0' -H 'Content-Type: application/json' -H 'Referer: http://localhost:4200' -H 'User-Agent: Mozilla/5.0' -d '${requestBody}' ${apiUrl}  "
                     def response = sh(script: curlCommand, returnStdout: true).trim()
                     println("auth API response >> "+response)
                     def startIndex = response.indexOf("token")+8
                     def endIndex = response.indexOf("tenant")-3
                     authToken = response.substring(startIndex,endIndex)
                     println(authToken)
                     sh "echo ${authToken} > authToken.txt"
                      	   

				def apiUrl1 = "${params.inputApiBaseURL}/api/v1/cdpinsights/ipsafe/pipeline/utility"

               dest = sh(script: " [ -d '${primaryDest}' ] && '${primaryDest}' || echo  '${fallBack}'", returnStdout: true).trim()

            //This step is to push reports to parent IpSafepipeline workspace
            
                //sh "sudo cp ${WORKSPACE}/${PROJECT}/dependency-check-report.xlsx ${WORKSPACE}/all_folders/CVC/${params.TIPAR_ID}/Security/CVCAndTechstack/${PROJECT} && cd ${WORKSPACE}/all_folders/CVC/${params.TIPAR_ID}/Security/CVCAndTechstack/${PROJECT} && mv dependency-check-report.xlsx CVC_${PROJECT}_Report.xlsx && mv CVC_${PROJECT}_Report.xlsx CVC_${PROJECT}_PrevReport.xlsx "
               // sh "sudo cp ${WORKSPACE}/${PROJECT}/syft_cyclonedx.json ${WORKSPACE}/all_folders/CVC/${params.TIPAR_ID}/Security/CVCAndTechstack/${PROJECT}"
                //sh " sudo cp ${WORKSPACE}/${PROJECT}/syft_cyclonedx.json ${fallBack}/Security/CVCAndTechstack/${PROJECT}"
                //sh " sudo cp ${WORKSPACE}/${PROJECT}/dependency-check-report.xlsx ${fallBack}/Security/CVCAndTechstack/${PROJECT}"
                
            //Generating RAG for updated excel

               sh "java -jar ${Grype_Home}/CVC_Grype_RAG_Genenrator-v1.3.jar -inputFile ${WORKSPACE}/${PROJECT}/dependency-check-PrevReport.xlsx -reportName Merged -destPath ${WORKSPACE}/${PROJECT}/"

               if (params.Include_CVC_Agent){
               sshPublisher(publishers: [sshPublisherDesc(configName: "${params.ScriptServer}",transfers: [ sshTransfer(execCommand: """sudo -n /bin/sh '${params.BUILD_ROOT}/nfs_shared_files/create_dependency_tree.sh' '${params.BUILD_ROOT}/nfs_shared_files/${params.PROJECT}/${params.VERSION}' """)],verbose: true)])

               sh ''' sudo cp "${WORKSPACE}/${PROJECT}/dependency-check-PrevReport.xlsx" "/shared_files/${PROJECT}/" '''
               sh """ export HTTPS_PROXY=http://172.17.0.11:8080 && export HTTP_PROXY=http://172.17.0.11:8080 && . /var/jenkins_home/CVC_autofix/bin/activate && cd ${env.WORKSPACE}/${PROJECT}/ && python3 ${WORKSPACE}/Agentic_DSO_Scripts/cvc_agent/get_recommended_versions.py 'dependency-check-PrevReport.xlsx' ${params.repo} ${params.branch} ${params.Priority} ${GIT_CRED_MCCI} /shared_files/${PROJECT}/${VERSION}/${params.repo}"""
                
                sh ''' sudo cp "${WORKSPACE}/${PROJECT}/safe_versions_result.json" "/shared_files/${PROJECT}/" '''


                

                def mcci_token = sh(script: "export HTTPS_PROXY=http://172.17.0.11:8080 && export HTTP_PROXY=http://172.17.0.11:8080 && . /var/jenkins_home/CVC_autofix/bin/activate && cd ${env.WORKSPACE}/${PROJECT}/ && python3 ${WORKSPACE}/Agentic_DSO_Scripts/cvc_agent/keycloak_auth.py", returnStdout: true).trim()

                def apiUrl_mcci = "https://172.18.228.83:7443/cip/genai/v1/agent/execute/sync" +
                                "?agent_id=92" +
                                "&agent_input=%7B%20%20%22user_query%22%3A%20%22hi%22%2C%20%20%22context_metadata%22%3A%20%22%7B%20%20%20%20%5C%22project_name%5C%22%3A%20%5C%22${PROJECT}%5C%22%2C%20%20%20%20%5C%22priority%5C%22%3A%20%5C%22${params.Priority}%5C%22%2C%20%20%20%20%5C%22version%5C%22%3A%20%5C%22${VERSION}%5C%22%2C%20%20%20%20%5C%22git_repo%5C%22%3A%20%5C%22${params.repo}%5C%22%2C%20%20%20%20%5C%22parent_path%5C%22%3A%20%5C%22${params.git_parent_folder}%5C%22%2C%20%20%20%20%5C%22base_path%5C%22%3A%20%5C%22${params.base_path}%5C%22%20%20%7D%22%2C%20%22flag%22%3A%20%22${params.Include_Autofix}%22%20%7D"
                   // ---- Execute Curl ----
                   
                def curl_mcci = "http_proxy=\"\" https_proxy=\"\" curl -k -X POST \"${apiUrl_mcci}\" -H \"Host: inbbrsefvm83\" -H \"accept: application/json\" -H \"Authorization: Bearer ${mcci_token}\" -H \"Content-Type: multipart/form-data\" -F \"files=string\""
                def resp_mcci = sh(script: curl_mcci, returnStdout: true).trim()
                println("auth API response >> "+resp_mcci)

                sh ''' sudo cp "/shared_files/${PROJECT}/agentic_interim_dp_report.xlsx" "${WORKSPACE}/${PROJECT}/" '''
                sh ''' sudo cp "/shared_files/${PROJECT}/Final_analized_report_sscportal.json" "${WORKSPACE}/${PROJECT}/" '''

                if (params.Include_Autofix == "yes"){
                sh ''' sudo cp "/shared_files/${PROJECT}/agentic_dependency-check-report.xlsx" "${WORKSPACE}/${PROJECT}/" '''
                }
                
                sh "echo '============creating application and version in ssc portal========='"

                sh """ java -jar /var/jenkins_home/workspace/SSC_FPR_transfer_1.6.jar -sscUrl http://172.18.228.112:8080/ssc -projectName1 ${params.PROJECT}_agentic_CVC_report -projectVersion1 '7.0' -userName 'admin' -password 'Password@123456!' """

                sh "echo '===========Fetching token from SSC portal==========='"

                def responsetoken = sh(script: """ curl --location --request POST 'http://172.18.228.112:8080/ssc/api/v1/tokens' -u 'admin:Password@123456!' --header 'Content-Type: application/json' --data-raw '{"type":"UnifiedLoginToken"}' """,returnStdout:true).trim()

                def token = sh(script: "echo '${responsetoken}' | jq -r '.data.token' ",returnStdout: true).trim()
                env.SSC_TOKEN = token
                echo "Token fetched: ${token}"
				
				sh  "echo '======== fetching project version id from utility jar and saving it in responsefile=========='"
                    
                sh """ java -jar /var/jenkins_home/cvc/workspace/FetchProjectVersionSSC_v1.1.jar -sscUrl http://172.18.228.112:8080/ssc -userName admin -password Password@123456! -projectName '${params.PROJECT}_agentic_CVC_report' -projectVersion '7.0' """
				
				sh "echo '===========fetching project version id from response file===='"

                def versionId = sh(script:   " jq -r '.id' ${env.WORKSPACE}/projectVersionId.json", returnStdout: true).trim()
                             
                            //echo "Extracted Project Version ID: ${versionId}"
                env.PROJECT_VERSION_ID = versionId
                echo "VersionId fetched: ${env.PROJECT_VERSION_ID}"
				sh "echo '========= uploading merged cvc json report in ssc portal =========='"
				sh """export HTTPS_PROXY=http://172.17.0.11:8080 && export HTTP_PROXY=http://172.17.0.11:8080 && cd ${env.WORKSPACE}/${PROJECT}/ && curl -X POST 'http://172.18.228.112:8080/ssc/api/v1/projectVersions/${env.PROJECT_VERSION_ID}/artifacts?engineType=OWASP_DEPCHECK' -H 'Authorization:FortifyToken ${env.SSC_TOKEN}' -H 'fileTokenType:UPLOAD' -F 'file=@Final_analized_report_sscportal.json' """

               }

                
            // Pushing issues data to dashboard
                
               callInsightsAPI("${WORKSPACE}/${PROJECT}/CVC_Merged_RAG.json",apiUrl1,buildDate,authToken,"Dependency Check",params.PIPELINE)
                
            //Renaming current dependency check excel report 
                //sh "mv ${WORKSPACE}/${PROJECT}/dependency-check-report.xlsx ${WORKSPACE}/${PROJECT}/dependency-check-prevReport.xlsx"



def callInsightsAPI(String outputPath, String apiUrl,String buildDate,String authToken,String stageName,String pipeline){
    echo "callInsightsAPI method for pipeline ${pipeline}"
      echo "Calling ResponseTIME INSIGHTS API"
        def jsonOutput = readFile(outputPath)
      echo "${jsonOutput}"
      jsonOutput=jsonOutput.trim();
      if(!(jsonOutput.startsWith('{'))){
          jsonOutput = "{${jsonOutput}}"
      }
    // def buildNum = sh(returnStdout: true, script: "cat /var/jenkins_home/jobs/${JOB_BASE_NAME}/builds/${BUILD_NUMBER}/log | head -1 | awk '{print \$NF}' | tr -d '\\n'")
   //   echo "${buildNum}"
       def buildNum1=params.BUILD_NUM
   // def buildNum1=894
      echo "${pipeline}"
      jsonOutput = jsonOutput[0..<jsonOutput.size() -1]+ ',"pipeline":\"'+"${pipeline}"+'\","stage":\"'+"${stageName}"+'\","buildNumber":\"'+"${BUILD_NUM}"+'\","buildDate":\"'+"${buildDate}"+'\"}'
       echo "${jsonOutput}"
       
		   echo "stage utility"
			 
			   def curlCommand = "unset http_proxy && unset https_proxy && curl -X POST -H 'api-key: c2VjcmV0' -H 'Content-Type: application/json' -H 'auth-token: ${authToken}' -H 'Referer: http://localhost:4200' -H 'User-Agent: Mozilla/5.0' -d '${jsonOutput}' ${apiUrl}"
               def response1 = sh(script: curlCommand, returnStdout: true).trim()
               println("response1 >>> "+response1)
    
}



