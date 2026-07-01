agentName  = "nfs_sast_node"
agentLabel = "${-> println 'Right Now the Agent Name ' + agentName; return agentName}"
def logContent
def FPR_FILE

pipeline {
    


    tools {
        maven "Maven_3.6"
        jdk "jdk8"
    }

    environment {
        JENKINS_MOUNT = "${params.JENKINS_MOUNT}"
        PROJECT       = "${params.PROJECT}"
        VERSION       = "${params.VERSION}"
        COMPONENT     = "${params.COMPONENT}"
        FORTIFY_SSC_SERVER_IP = "${params.FORTIFY_SSC_SERVER_IP}"
        SAST_NODE = "${params.SAST_NODE}"
        SAST_JENKINS_NODE = "${params.SAST_JENKINS_NODE}"
        TIPAR_ID      = "${params.TIPAR_ID}"
        FOLDER        = "${params.FOLDER}"
        // Note: executing 'sh' in environment can be flaky across agents; we’ll also compute in a step.
        currentTime   = sh(returnStdout: true, script: 'date +%d-%m-%Y').trim()
        APPLLICATION_VERSION_IN_SSC = "${params.APPLLICATION_VERSION_IN_SSC}"
        APPLLICATION_VERSION_IN_SSC_OLD = "${params.APPLLICATION_VERSION_IN_SSC_OLD}"
        PREV_VERSION = "${params.PREV_VERSION}"
    }

    agent { label "${params.SAST_JENKINS_NODE}" }

    stages {

        stage('clean_workspace') {
            steps {
                script {
                    echo "============ copying audit data (dev comments) from previous application version in ssc portal to current application version in ssc portal ========="
                    
                   // sh "java -jar /var/jenkins_home/Softwares/SAST/SSC_FPR_transfer_1.7_debug.jar -sscUrl 'http://172.18.228.112:8080/ssc' -userName admin -password Password@123456! -projectName1 ${params.PROJECT}_${params.APPLLICATION_VERSION_IN_SSC} -projectVersion1 ${params.PREV_VERSION} -projectName2 ${params.PROJECT}_${params.APPLLICATION_VERSION_IN_SSC} -projectVersion2 ${params.VERSION}"
                    sh "java -jar /var/jenkins_home/Softwares/SAST/SSC_FPR_transfer_1.7_debug.jar -sscUrl 'http://172.18.228.112:8080/ssc' -userName admin -password Password@123456! -projectName1 ${params.PROJECT}_${params.APPLLICATION_VERSION_IN_SSC} -projectVersion1 ${params.PREV_VERSION} -projectName2 ${params.PROJECT}_${params.APPLLICATION_VERSION_IN_SSC} -projectVersion2 ${params.VERSION}"
                    sh 'find "${WORKSPACE}" -type f -mtime +3 -exec rm {} + || true'
                }
            }
        }

        stage('stashfile') {
            steps {
                sh 'pwd'
                script {
                    FPR_FILE = "${PROJECT}_${params.APPLLICATION_VERSION_IN_SSC}"
                    sh "echo FPR_FILE=${FPR_FILE}"
                    // Ensure currentTime computed (if env var failed)
                    if (!env.currentTime?.trim()) {
                        env.currentTime = sh(returnStdout: true, script: 'date +%d-%m-%Y').trim()
                    }
                }
            }
        }

        // Run SAST on ttgsastnode and STASH artifacts from there
        stage('sast') {
            agent { label "${params.SAST_NODE}" }
            steps {
                // Ensure we are in the desired drive/path on Windows agent
                bat "cd R:"+"\\"+params.PROJECT+"\\"+params.VERSION+"\\"+params.COMPONENT+''
			 
	//If you do not clean your project before you execute the build, then Fortify Static CodeAnalyzer only processesthose filesthat the build tool re-compiles.			  
	    fortifyClean buildID: ''+params.BASE_NAME+'', maxHeap: '20840',logFile: ''+params.BASE_NAME+'.log'
	
    fortifyTranslate buildID: ''+params.BASE_NAME+'', excludeList:''+excludeList+''  ,maxHeap: '50000',logFile: "${FPR_FILE}-translate.log" ,projectScanType:fortifyOther(otherIncludesList: "R:"+"\\"+params.PROJECT+"\\"+params.VERSION+"\\"+params.COMPONENT+''), verbose: true
    
    fortifyScan buildID: ''+params.BASE_NAME+'', resultsFile: "${FPR_FILE}.fpr",maxHeap: '50000', logFile: "Sast_Scan_${FPR_FILE}.log"
 
   fortifyUpload appName: "${FPR_FILE}", appVersion: ''+params.VERSION+'', resultsFile: "${FPR_FILE}.fpr",timeout: '465000'


                // Report generation (outputs into current directory)
                bat """@echo off
G:\\Software\\PDFGenerator\\Fortify_Report_Generator_75.bat \"${FPR_FILE}.fpr\" \"${WORKSPACE}\" \"${FPR_FILE}\" . exportreportadmin Password@1234567! http://172.18.228.112:8080/ssc \"${params.VERSION}\"
@echo on"""

                // STASH all outputs needed later (do this on ttgsastnode)
                // Include PDF, XML, XLSX, FPR, JSON (RAG), logs if needed.
                script {
                    // Use a unique stash name per build
                    def stashName = "sast-artifacts-${env.BUILD_NUMBER}"
                    echo "Stashing artifacts as: ${stashName}"
                    // The generator names you use downstream:
                    //  - ${FPR_FILE}_Developer_Report_${currentTime}.pdf
                    //  - ${FPR_FILE}_Developer_Report.xml
                    //  - SAST_${FPR_FILE}_Developer_Report_${currentTime}.xlsx
                    //  - ${FPR_FILE}.fpr
                    //  - ${FPR_FILE}_Developer_Report_${currentTime}_RAG.json
                    //currentTime   = sh(returnStdout: true, script: 'date +%d-%m-%Y').trim()
                    stash name: stashName, includes: """
                        ${FPR_FILE}_Developer_Report.pdf,
                        ${FPR_FILE}_Developer_Report.xml,
                        SAST_${FPR_FILE}_Developer_Report.xlsx,
                        ${FPR_FILE}.fpr,
                        ${FPR_FILE}_Developer_Report_RAG.json,
                        *.log
                    """.replaceAll(/\s+/, " ").trim()
                }
            }
        }

        stage('Archive Artifacts') {
            // default agent (master). We will UNSTASH here.
            steps {
                script {
                    // Ensure target directory exists for later 'cp'
                    def directory = new File("${params.PROJECT}")
                    if (fileExists("$directory")) {
                        println('The directory exists.')
                        echo "$directory"
                    } else {
                        sh "mkdir -p '${params.PROJECT}'"
                        sh "chmod -R 777 '${params.PROJECT}' || true"
                    }
                }

                // UNSTASH from the SAST node into current workspace
                script {
                    def stashName = "sast-artifacts-${env.BUILD_NUMBER}"
                    echo "Unstashing artifacts: ${stashName}"
                    unstash stashName
                }

                // Now archive from CURRENT workspace (no /shared_files path)
                archiveArtifacts allowEmptyArchive: true, artifacts: "${FPR_FILE}_Developer_Report.pdf", onlyIfSuccessful: true
                archiveArtifacts allowEmptyArchive: true, artifacts: "${FPR_FILE}_Developer_Report.xml", onlyIfSuccessful: true
                archiveArtifacts allowEmptyArchive: true, artifacts: "SAST_${FPR_FILE}_Developer_Report.xlsx", onlyIfSuccessful: true
                archiveArtifacts allowEmptyArchive: true, artifacts: "${FPR_FILE}.fpr", onlyIfSuccessful: true

                // Replace previous shared_files source paths with local workspace files
                // Copy JSON to the job workspace folder of TtgSast (as your original intent)
                sh "cd /var/jenkins_home/sast/workspace/TtgSast/ && mkdir -p ${params.PROJECT}/"
                sh "cp -f '${WORKSPACE}/${FPR_FILE}_Developer_Report_RAG.json' '/var/jenkins_home/sast/workspace/TtgSast/${params.PROJECT}/${FPR_FILE}_Developer_Report_RAG.json'"

                // Copy XLSX to the pipeline job folder
                sh "cp -f '${WORKSPACE}/SAST_${FPR_FILE}_Developer_Report.xlsx' '/var/jenkins_home/jobs/${PIPELINE}/workspace/all_folders/SAST/${TIPAR_ID}/'"
            }
        }
    }

    
}
