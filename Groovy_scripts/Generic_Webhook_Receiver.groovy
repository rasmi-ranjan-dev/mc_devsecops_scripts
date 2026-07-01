agentName = "ttgsastnode"
agentLabel = "${-> println 'Right Now the Agent Name ' + agentName; return agentName}"
def logContent
def flexnet_auth_token = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJwYWxhbWlkYSIsInVzZXJJZCI6MywiaWF0IjoxNzM0MDIzMjc0fQ.hyTpnABV54igsluUgja4G2G0ze_TC1P_mwRrcJXJySv02vKkpJJJokGvb2U0L0OB8ewIMWuwU_cUYnfTDAkWKQ"
def taskId
def extractedValue
def palamida_url = "http://10.32.47.15:8888"
def dest = ''


import groovy.json.JsonSlurper
import com.cloudbees.groovy.cps.NonCPS

// Helper to safely parse JSON in Jenkins pipeline
@NonCPS
def parseJson(String jsonText) {
    return new JsonSlurper().parseText(jsonText)
}

//agentName = "ttgsastnode"
pipeline {
    // agent any
    //----------------adding new code for checking new features--------------------------
    triggers {
        GenericTrigger(
            genericVariables: [
                //common variables
                [key: 'SCAN_TYPE', value: '$.SCAN_TYPE'], 
                [key: 'USERNAME', value: '$.USERNAME'], 

                //For Sast payload variables
                [key: 'FPR_FILE', value: '$.FPR_FILE'], 
                [key: 'SSC_URL', value: '$.sscUrl'], 
                [key: 'COMPONENT', value: '$.COMPONENT'], 
                [key: 'BASENAME', value: '$.BASENAME'], 
                [key: 'VERSION', value: '$.VERSION'], 
                [key: 'BUILD_TYPE', value: '$.buildType'], 
                                
                            
                //For CVC payload variables
                [key: 'status', value: '$.status'], 
                [key: 'timestamp', value: '$.timestamp'], 
                [key: 'reportUrl', value: '$.reportUrl'], 
                [key: 'PARENT_FOLDER', value: '$.parentFolder'], 
                [key: 'SCAN_TYPE', value: '$.SCAN_TYPE'], 
                [key: 'PARENT_BUILD_NUM', value: '$.buildID'], 

                 //For OSS
                [key: 'PROJECT', value: '$.project']
            ], 
            token: 'dso_generic_receiver_token', // secret token configured in SSC
            printContributedVariables: true, 
            printPostContent: true, 
            //only trigger if username ==admin
            regexpFilterText: '$USERNAME', 
            regexpFilterExpression: '^admin$'
        )
    }
    
    environment {
        SSC_URL = "http://172.18.228.112:8080/ssc"
        SSC_API_TOKEN = "ZTRjYmMxNjEtNjdkYS00ZDdlLWI0YzMtZDNmODlmNWI0ZDM0"
        currentTime = sh(returnStdout: true, script: 'date +%d-%m-%Y').trim()
        REMOTE_DIR = "151"
    }
    
    parameters {
        string(defaultValue: 'http://172.18.228.57:8091', description: 'Application URL', name: 'inputApiBaseURL')
        booleanParam(defaultValue: false, description: 'Enable to create each defect for each sast issue', name: 'devplusDefectForSecIssues')
        //string(defaultValue: 'JileALM_7.2_IPSafeV1_M_Asyc_Full',name:'PIPELINE',description:' Configured Pipeline ')
    }

    //---------------------end------------------------------
    agent {
        label 'master'
    }
    stages {
        stage('Get Project Details') {
            steps {
                script {
                    echo "Inside Get Project Details"
                    //def projconfigPath = "/shared_files/Orchestrator/Projects/Jile/Jile_7_2.yaml"
                    def projectName = env.PROJECT?.trim()
                    def projconfigPath = ""

                    echo "Project received from webhook: ${projectName}"

                    if (projectName == "Jile") {
                        projconfigPath = "/shared_files/Orchestrator/Projects/Jile/Jile_7_2.yaml"
                    } else if (projectName == "Insights_PLAC") {
                        projconfigPath = "/shared_files/Orchestrator/Projects/Insights_PLAC/Insights_PLAC_test_7.0.yaml"
                    } else {
                        error "Unsupported or missing project value from webhook: ${projectName}"
                    }

                    echo "Reading orchestration YAML from: ${projconfigPath}"
                    // Parse YAML directly from the file
                    def cfg = readYaml file: projconfigPath
                    // Access keys (adjust to your YAML structure)
                    /*
                    def PROJECT = cfg?.projectname
                    def VERSION = cfg?.scm_branch
                    def TIPAR_ID = cfg?.tipar_id
                    */
                    env.PROJECT = cfg?.projectname
                    env.VERSION = cfg?.scm_branch
                    env.TIPAR_ID = cfg?.tipar_id
                    if (!env.PROJECT || !env.VERSION) {
                        error "YAML missing required keys: projectname/scm_branch. Got: ${cfg}"
                    }
                    echo "PROJECT=${env.PROJECT}, VERSION=${env.VERSION}, TIPAR_ID=${env.TIPAR_ID}"
                }
            }
        }		
        stage('Read CVC YAML and set globals') {
            when {
                expression {
                    return env.SCAN_TYPE?.trim() == "CVC"
                }
            }
            steps {
                // If you no longer need to copy the file, you can remove this sh block.
                // Keeping as-is since you included it.
                dir("async_full_orchestration") {
                    sh '''
            sudo cp scan_payload.yaml \
            /var/jenkins_home/jobs/Generic-Webhook-Receiver/workspace/
          '''
                }

                script {
                    // 1) Ensure YAML exists and read it (file at workspace root)
                    def yamlPath = "${WORKSPACE}/scan_payload.yaml"
                    if (!fileExists(yamlPath)) {
                        error "YAML file not found at: ${yamlPath}"
                    }
                    def data = readYaml file: yamlPath

                    // 2) Get the CVC section
                    def cvc = data.cvc
                    if (!(cvc instanceof Map)) {
                        error "YAML does not contain 'cvc' section as a map."
                    }

                    // 3) Helper closure: convert a key to UPPER_SNAKE
                    def toUpperSnake = {
                        String s -> s.replaceAll(/([a-z0-9])([A-Z])/, '$1_$2').toUpperCase()
                    }

                    // 4) Export ALL cvc keys to uppercase env vars
                    cvc.each {
                        k, v -> def upperKey = toUpperSnake(k as String)   // e.g., tiparId -> TIPAR_ID
                        env."${upperKey}" = (v == null) ? '': v.toString()  // <-- FIXED
                    }

                    // 5) Local Groovy vars for your echo block
                    def status = cvc.status ?: 'SUCCESS'
                    def reportUrl = cvc.reportUrl ?: ''
                    def timestamp = cvc.timestamp ?: ''        // if absent, blank
                    def primaryDest = cvc.primaryDest ?: ''
                    def fallBack = cvc.fallBack ?: ''
                    //def buildDate   = cvc.buildDate   ?: ''
                    // 6) Mirror to env for ${env.*} usage where you expect lowercase names
                    env.status = status
                    env.reportUrl = reportUrl
                    env.timestamp = timestamp
                    env.primaryDest = primaryDest
                    env.fallBack = fallBack
                    //env.buildDate   = buildDate
                    env.paths = cvc.paths ?: ''
                }
            }
        }

        stage('Print CVC Webhook Data') {
            when {
                expression {
                    return env.SCAN_TYPE?.trim() == "CVC"
                }
            }
            steps {
                echo "Project: ${PROJECT}"
                echo "Status: ${status}"
                echo "Report: ${reportUrl}"
                echo "Timesteamp: ${timestamp}"
                echo "Paths: ${env.paths}"
                echo "TIPAR_ID: ${env.TIPAR_ID}"
                echo "PIPELINE: ${env.PIPELINE}" 
                echo "PARENT_FOLDER: ${env.PARENT_FOLDER}" 
                echo "primaryDest: ${env.primaryDest}" 
                echo "fallBack: ${env.fallBack}" 
                //echo "buildDate: ${env.buildDate}" 
                echo "VERSION: ${env.VERSION}"
            }
        }

        stage('after webhook call') {
            when {
                expression {
                    return env.SCAN_TYPE?.trim() == "CVC"
                }
            }
            steps {
                script {
                    //def Scanreports="/var/jenkins_home/cvc/workspace/${PROJECT}"
                    def PARENT_FOLDER = "${env.PARENT_FOLDER}"
                    def PARENT_BUILD_NUM = "${env.PARENT_BUILD_NUM}"
                    echo "parent build number :  ${env.PARENT_BUILD_NUM}"
                    def PARENT_PIPELINE = "${env.PIPELINE}"
                    echo "parent PIPELINE -- :  ${PARENT_PIPELINE}"
                    //code aaded for merge pipline
                    //def Scanreports="/var/jenkins_home/cvc/workspace/JileALM_7.2_IPSafeV1_M_Asyc_Full/${PROJECT}"
                    //def paths="${env.paths}"
                    //def buildDate="${env.buildDate}"
                    def fallBack = "${env.fallBack}"
                    def primaryDest = "${env.primaryDest}"
                    def PROJECT = "${env.PROJECT}"
                    def VERSION = "${env.VERSION}"
                 
                    echo " version is  --> ${env.VERSION}"

                    def paths = "/shared_files/${PROJECT}/${VERSION}/${PARENT_FOLDER}"
                    //def Scanreports="/var/jenkins_home/jobs/Generic-Webhook-Receiver/workspace/${PROJECT}"
                    def Scanreports = "${WORKSPACE}/${PROJECT}"
                    def reportOutput = "all_folders/CVC/${env.TIPAR_ID}/Security/CVCAndTechstack/${PROJECT}"
               
                    //code aaded for merge pipline
                    // def srcDir = new File("/var/jenkins_home/cvc/workspace/JileALM_7.2_IPSafeV1_M_Asyc_Full/${project}")
                    // def destDir = new File("/var/jenkins_home/jobs/Generic-Webhook-Receiver/workspace/${project}")
                    def files = ['dependency-check-report.xml', 'dependency-check-report.json']
                    //added code to create the project in receiver pipeline and copy the xml and json file 
                    def directory = new File("${WORKSPACE}" + "/" + "${env.PROJECT}"); 
                    if (directory.exists()) {
                        echo  "The directory is present --> $directory"
                    } else {
                        echo  "The directory is not present --> $directory"
                        sh "sudo mkdir -p ${env.PROJECT}"
                    }

                    /*  sh """
                    mkdir -p '${destDir}'
                    if [ -f '${srcDir}/dependency-check-report.xml' ] && [ -f '${srcDir}/dependency-check-report.json' ] || [ -f '${srcDir}/report_grype.csv' ] ; then
                        sudo cp '${srcDir}/dependency-check-report.xml' '${srcDir}/dependency-check-report.json'  '${destDir}/'
                        echo "Copied XML and JSON reports to ${destDir}"
                    else
                        echo "Warning: One or both Dependency-Check report files are missing in ${srcDir}"
                    fi
                """
               */ 
                    def srcDir = "/var/jenkins_home/jobs/JileALM_7.2_IPSafeV1_M_Asyc_Full/workspace/${project}"
                    def destDir = "${WORKSPACE}/${project}"


                    sh """
                    mkdir -p "${destDir}"
                    cp -r "${srcDir}/." "${destDir}/"
                    echo "Copied all files from ${srcDir} to ${destDir}"
                """

      
                    def report_path = "**/${PROJECT}/dependency-check-report.xml"
                    dependencyCheckPublisher pattern: "$report_path"
                    archiveArtifacts allowEmptyArchive: true, artifacts: "${env.PROJECT}/dependency-check-report.xml", onlyIfSuccessful: true
                    echo "${env.PROJECT}"
                    sh "echo $PARENT_FOLDER"
                    sh "echo ${env.FOLDER}"
                
               
                   
                    /* echo "======= getting cvc issues from ssc portal and saving it to rag.json using api =========="
 
                sh """ java -jar /var/jenkins_home/Softwares/CVC/SSC_Issues_RAG.jar -projectName ${env.PROJECT}_CVC_ADAST  -projectVersion ${VERSION} -sscUrl http://172.18.228.112:8080/ssc -username admin -password Password@123456! -ssc -scanType OWASP_DEPCHECK -outputPath ${env.WORKSPACE}/${reportOutput} """
        
               echo "========= pushing rag.json to dashboard ======"     
*/
                    sh  "java -jar /var/jenkins_home/Softwares/CVC/CVCAndTechstack_v7.2_patch.jar -proxyHost 172.17.0.11 -proxyPort 8080 -cvcXml $Scanreports/dependency-check-report.xml -moduleName ${PROJECT} -outputPath ./${reportOutput} -techStackXlsx ${paths}/${PROJECT}_Techstack.xlsx -metaDataXlsx ${paths}/${PROJECT}_Metadata.xlsx"

                    def apiUrl = "${params.inputApiBaseURL}/api/v1/authentication/auth"

                    def requestBody = '{"user_name":"' + "cdpadmin" + '","password":"' 
                        + "Password@1" + '","accpetedMsgId":null}'

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

                    def apiUrl1 = "${params.inputApiBaseURL}/api/v1/cdpinsights/ipsafe/pipeline/utility"
                     
                      
                    //modify the primaryDest value
                    primaryDest = "/var/jenkins_home/jobs/Generic-Webhook-Receiver/workspace/all_folders/CVC/TIPAR-022025-000016"
                     
                    def buildDate = sh(returnStdout: true, script: 'date').trim()
                    echo "Build Date: ${buildDate}"

                    //callInsightsAPI("${env.WORKSPACE}/${reportOutput}/CVC_${env.PROJECT}_CVC_ADAST_${VERSION}_RAG.json",apiUrl1,buildDate,authToken,"Dependency Check",params.PIPELINE,PARENT_BUILD_NUM)
                    callInsightsAPI("${env.WORKSPACE}/${reportOutput}/CVC_dependency-check-report_RAG.json", apiUrl1, buildDate, authToken, "Dependency Check", params.PIPELINE, PARENT_BUILD_NUM)
                     
                    
                    //Renaming current dependency check excel report 
                    // sh "mv ${WORKSPACE}/${PROJECT}/dependency-check-report.xlsx ${WORKSPACE}/${PROJECT}/dependency-check-prevReport.xlsx"
                }
            }
        }		
       		
    
        stage('Print OSS Webhook Data') {
            when {
                expression {
                    return env.SCAN_TYPE?.trim() == "OSS"
                }
            }
   
            steps {
                script {
                    echo "Enter on to the OSS webhook "
                    echo "Project: ${PROJECT}"
                    echo "SCAN_TYPE: ${SCAN_TYPE}"
                    echo "STATUS: ${STATUS}"
                    echo "USERNAME: ${USERNAME}"
                }
            }
        }
    
        stage('OSS Status Push') {
            when {
                expression {
                    return env.SCAN_TYPE?.trim() == "OSS"
                }
            }
            steps {
                script {
                    echo 'Running RAG Generator...'
               
                    sh """
                            curl -X POST \
                            "$palamida_url/codeinsight/api/projects/${REMOTE_DIR}/reports/1/generate" \
                            -H "accept: application/json" \
                            -H "Authorization: Bearer $flexnet_auth_token" \
                            -H "content-type: application/json" -d "{}" > response.json
                        """
                    taskId = sh(script: "jq -r '.data.taskId' response.json", returnStdout: true).trim()

                    sh "echo Task Id is: $taskId"
                    
                    // build job: 'OSS_Scan_pipeline',parameters: [string(name: 'PIPELINE', value: "${params.PIPELINE}"),string(name: 'REMOTE_DIR', value: "${params.REMOTE_DIR}"),string(name: 'PARENT_BUILD_NUM', value: "${params.BUILD_NUM}")]
                }
            }
        }
   
   
        stage('SAST Webhook Data') {
            when {
                expression {
                    return env.SCAN_TYPE?.trim() == "SAST"
                }
            }
            steps {
                echo "Project: ${env.PROJECT}"
                echo "VERSION: ${env.VERSION}" 
                echo "Status: ${status}"
                echo "FPR_FILE: ${env.FPR_FILE}"
                echo "COMPONENT: ${env.COMPONENT}" 
                echo "BASENAME: ${env.BASENAME}" 
                echo "USERNAME: ${env.USERNAME}" 
                echo "BUILD_TYPE: ${env.BUILD_TYPE}"
                echo "BUILD_ID: ${env.PARENT_BUILD_NUM}"
            }
        }


        stage('Generate Fortify Report') {
            when {
                expression {
                    return env.SCAN_TYPE?.trim() == "SAST"
                }
            }  
            agent {
                label 'nfs_sast_jile_node'
            }
           
            options {
                skipDefaultCheckout(true) // ✅ stop auto-checkout first
            }

            steps {
                script {
                    // echo "Downloading FPR for Artifact ID: ${ARTIFACT_ID}"
                    echo "Saving to workspace: ${WORKSPACE}\\${FPR_FILE}"
                  
                    /* def fprPath = env.FPR_FILE

                    // Normalize double-backslashes to single (just in case)
                    fprPath = fprPath.replace('\\\\', '\\')

                    // Extract file name without extension
                    def fprName = new File(fprPath).getName().replaceFirst(/\.fpr$/, '')

                    echo "FprName = ${fprName}"
                    */

                    def fprPath = env.FPR_FILE.replace('\\\\', '\\')

                    // Extract only the last part after the final backslash
                    def fprName = fprPath.tokenize('\\')[-1]

                    // Remove .fpr if present
                    fprName = fprName.replaceFirst(/\.fpr$/, '')

                    echo "FprName = ${fprName}"
                    
                  // Promote to environment variable
                      env.FPR_Name = fprName


				/*  
					bat """
					@echo off
					setlocal enabledelayedexpansion

                    
                    echo BUILD_TYPE=%BUILD_TYPE%
                    echo FPR_FILE=%FPR_FILE%

                    REM Normalize path: convert \\ to \
                    set "FPR_PATH=%FPR_FILE:\\\\=\\%"

                    REM Extract filename safely
                    REM for %%F in ("!FPR_PATH!") do set "FPR_NAME=%%~nxF"

                    REM echo FPR_NAME=!FPR_NAME!


					REM BUILD_TYPE, PARENT_BUILD_NUM, COMPONENT, PROJECT, VERSION must be present in env

					IF /I "%BUILD_TYPE%"=="incremental" (
					  set "TARGET=R:\\%PROJECT%\\incremental\\%PARENT_BUILD_NUM%\\output"

					) ELSE (
					  set "TARGET=R:\\%PROJECT%\\%VERSION%\\%COMPONENT%"
					)

					echo TARGET=!TARGET!

					if not exist "!TARGET!" (
					  echo Directory not found: !TARGET!
					  exit /b 0
					)

                    
                    echo Copying from !TARGET! ...
                    copy "!TARGET!\\*.fpr" "%WORKSPACE%"


					cd /d "!TARGET!"
					rem fortifyclient uploadFPR -file "${FPR_FILE}" -application "${env.PROJECT}_${env.BASENAME}" -applicationVersion "${env.VERSION}" -url "${SSC_URL}" -authtoken "${SSC_API_TOKEN}"
                     
					  
					"""
        */
         
         bat """
            @echo off
            setlocal enabledelayedexpansion

            echo BUILD_TYPE=%BUILD_TYPE%
            echo PROJECT=%PROJECT%
            echo VERSION=%VERSION%
            echo COMPONENT=%COMPONENT%
            echo FPR_FILE=%FPR_FILE%
            echo WORKSPACE=%WORKSPACE%

            REM Use the FPR file path received directly from webhook
            set "SOURCE_FPR=%FPR_FILE%"

            echo Checking source FPR file:
            echo !SOURCE_FPR!

            if not exist "!SOURCE_FPR!" (
                echo ERROR: FPR file not found at webhook path: !SOURCE_FPR!
                exit /b 1
            )

            echo Copying FPR to Jenkins workspace...
            copy /Y "!SOURCE_FPR!" "%WORKSPACE%\\${fprName}.fpr"

            if errorlevel 1 (
                echo ERROR: Failed to copy FPR file to workspace.
                exit /b 1
            )

            if not exist "%WORKSPACE%\\${fprName}.fpr" (
                echo ERROR: FPR file not found in workspace after copy.
                exit /b 1
            )

            echo FPR copied successfully:
            dir "%WORKSPACE%\\${fprName}.fpr"
          """

          fortifyUpload appName: "${fprName}", appVersion: ''+env.VERSION+'', resultsFile: "${fprName}.fpr",timeout: '365000'
					echo "Here code for Downloading PDF and XLS file"
          def FORTIFY_SSC_SERVER_IP= "http://172.18.228.112:8080/ssc/"
                   
                   
           // G:\\Software\\PDFGenerator\\Fortify_Report_Generator_75.bat \"${FPR_FILE}.fpr" "R:\\workspace\\SAST_Webhook_Receiver\"${FPR_FILE}"  "R:\\workspace\\${env.JOB_NAME}\\${PROJECT}\\${COMPONENT}\\${VERSION}" exportreportadmin password@123456 "${FORTIFY_SSC_SERVER_IP}" """+params.VERSION+"""
                     
            bat """@echo off
                    
              G:\\Software\\PDFGenerator\\Fortify_Report_Generator_75.bat \"${fprName}.fpr" """+WORKSPACE+""" \"${fprName}" . exportreportadmin password@123456 http://172.18.228.112:8080/ssc ${env.VERSION}
                       
            @echo on"""
                    
                    //code for pdf generation
               def fprFile = "${env.WORKSPACE}\\${fprName}.fpr"
               def dateSuffix = new Date().format("yyyyMMdd")
               def reportFileName = "${PROJECT}_${BASENAME}_${VERSION}_report_${currentTime}.pdf"
               def reportFileName1 = "${PROJECT}_${BASENAME}_${VERSION}_report_${currentTime}.xml"
                         
                     
                bat "\"C:\\Program Files\\Fortify\\OpenText_Application_Security_Tools_25.4.0\\bin\\ReportGenerator.bat\" -format XML -f ${reportFileName1} -source \"${fprFile}\""
                                  
                bat "\"C:\\Program Files\\Fortify\\OpenText_Application_Security_Tools_25.4.0\\bin\\ReportGenerator.bat\" -format PDF -f ${reportFileName} -source \"${fprFile}\""
                      

                    
                                     
                }
            }
        }

      stage("Archive Artifacts"){
         when {
          expression {
              return env.SCAN_TYPE?.trim() == "SAST"
          }
      }
           //agent{label 'nfs_sast_node'}
           steps{
       
        dir('/shared_files/workspace/Generic-Webhook-Receiver'){
     
          archiveArtifacts allowEmptyArchive: true, artifacts: "${FPR_Name}_Developer_Report_${currentTime}.pdf", onlyIfSuccessful: true
          archiveArtifacts allowEmptyArchive: true, artifacts: "${FPR_Name}_Developer_Report.xml", onlyIfSuccessful: true
          archiveArtifacts allowEmptyArchive: true, artifacts: "SAST_${FPR_Name}_Developer_Report_${currentTime}.xlsx", onlyIfSuccessful: true
          archiveArtifacts allowEmptyArchive: true, artifacts: "${FPR_Name}.fpr", onlyIfSuccessful: true    
          
         }	
 
        script{
          def directory = new File("${env.PROJECT}")
              if (fileExists("$directory")){
              println('The directory exists.')
              echo  "$directory"
            }else{
              sh "mkdir -p "+ env.PROJECT
              sh "chmod -R 777 "+env.PROJECT
            }
        } 

          sh "ls -l /var/jenkins_home/jobs/Generic-Webhook-Receiver/workspace/" + env.PROJECT

        //Copy component wise json to build type folder 

        //sh "cp -f /shared_files/workspace/Generic - Webhook - Receiver/${FPR_FILE}_Developer_Report_${currentTime}_RAG.json /var/jenkins_home/jobs/SAST_Webhook_Receiver/workspace/" + env.PROJECT

        //To archive excel report in parent pipeline
        //sh "cp -f /shared_files/workspace/Generic - Webhook - Receiver/SAST_${FPR_FILE}_Developer_Report_${currentTime}.xlsx /var/jenkins_home/jobs/IPSafe_pipeline_Copyinsights_PLAC/workspace/all_folders/SAST/${TIPAR_ID}"

   }                
    
}   


   
      
    } //End Stages

    
} //pipeline End


//code added for callInsightsAPI
def callInsightsAPI(String outputPath, String apiUrl,String buildDate,String authToken,String stageName,String pipeline,String buildNum1){
    echo "callInsightsAPI method for pipeline ${pipeline}"
      echo "Calling ResponseTIME INSIGHTS API"
        def jsonOutput = readFile(outputPath)
      echo "${jsonOutput}"
      jsonOutput=jsonOutput.trim();
      if(!(jsonOutput.startsWith('{'))){
          jsonOutput = "{${jsonOutput}}"
      }
    // def buildNum = sh(returnStdout: true, script: "cat /var/jenkins_home/jobs/${JOB_BASE_NAME}/builds/${BUILD_NUMBER}/log 
                        | head -1 | awk '{print \$NF}' | tr - d '\\n'")
   //   echo "$ {
                        buildNum
                    }"
       
       echo "buildNum1 is: $ {
                        buildNum1
                    }"
   
      echo "$ {
                        pipeline
                    }"
      jsonOutput = jsonOutput[0..<jsonOutput.size() -1]+ ',"pipeline":\"'+"$ {
                        pipeline
                    }"+'\","stage":\"'+"$ {
                        stageName
                    }"+'\","buildNumber":\"'+"$ {
                        buildNum1
                    }"+'\","buildDate":\"'+"$ {
                        buildDate
                    }"+'\"}'
       echo "$ {
                        jsonOutput
                    }"
       
		   echo "stage utility"
			 
			   def curlCommand = "unset http_proxy && unset https_proxy && curl - X POST 
                        - H 'api-key: c2VjcmV0' - H 'Content-Type: application/json' 
                        - H 'auth-token: ${authToken}' - H 'Referer: http://localhost:4200' 
                        - H 'User-Agent: Mozilla/5.0' - d '${jsonOutput}' $ {
                        apiUrl
                    }"
               def response1 = sh(script: curlCommand, returnStdout: true).trim()
               println("response1 >>  > "+response1)
    
}
