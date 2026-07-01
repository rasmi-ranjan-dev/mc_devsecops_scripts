 build job: 'SAST_V1', parameters: [string(name: 'FORTIFY_SSC_SERVER_IP', value: "${params.FORTIFY_SSC_SERVER_IP}"), string(name: 'PROJECT', value: "${params.PROJECT}"), string(name: 'VERSION', value: "${params.VERSION}"), string(name: 'FOLDER', value: "${params.FOLDER}"), string(name: 'COMPONENT_LIST', value: "${params.COMPONENT_LIST}"), string(name: 'TIPAR_ID', value: "${params.TIPAR_ID}"), string(name: 'PIPELINE', value: "${params.PIPELINE}"), string(name: 'BASENAME', value: "${params.BASENAME}"), string(name: 'PROJECT2', value: "${params.PROJECT2}"), string(name: 'PREV_VERSION', value: "${params.PREV_VERSION}"), string(name: 'SAST_NODE', value: "${params.SAST_NODE}"), string(name: 'SAST_JENKINS_NODE', value: "${params.SAST_JENKINS_NODE}")]

                                    
sh "echo $WORKSPACE"
dir("/var/jenkins_home/jobs/${PIPELINE}/workspace") {
    def reportpath = "all_folders/SAST/${params.TIPAR_ID}/*.xlsx"
    archiveArtifacts allowEmptyArchive: true, artifacts: reportpath, onlyIfSuccessful: true
}
