pipeline {
    //tools  used in pipeline  is mentioned in this   section     
    tools
    {
        maven "Maven_3.6"
        jdk "openjdk-8"
    }
    environment
    {
        REMOTE_DIR = "${params.REMOTE_DIR}"
        BUILD_NUM = "${currentBuild.getNumber()}"
        PROJECT = "${params.PROJECT}"
        VERSION = "${params.VERSION}"
        BUILD_TYPE = "${params.BUILD_TYPE}"
        BUILD_ROOT = "${params.BUILD_ROOT}"
        SCM_URL = "${params.SCM_URL}"
        NFS_SHARE = "${params.NFS_SHARE}"
        BUILD_SERVER_IP = "${BUILD_SERVER_IP}"
        COMPONENT_LIST = "${COMPONENT_LIST}"
        CHECKOUT_SCRIPT = "${CHECKOUT_SCRIPT}"
        JENKINS_MOUNT = "${params.JENKINS_MOUNT}"
        APPLICATION_URL = "${params.APPLICATION_URL}"
        GIT_CRED = credentials('git_cred')
        TIPAR_ID = "${params.TIPAR_ID}"
        PIPELINE = "${params.PIPELINE}"
        FOLDER = "${params.FOLDER}"
        FORTIFY_SSC_SERVER_IP = "${params.FORTIFY_SSC_SERVER_IP}"
        serverutilsation_path = "${params.serverutilsation_path}"
        jmeter_path = "${params.jmeter_path}"
        jmx_path = "${params.jmx_path}"
        jtl_path = "${params.jtl_path}"
        jmx_file = "${params.jmx_file}"
        jtl_file = "${params.jtl_file}"
        jmeter_server_ip = "${params.jmeter_server_ip}"
        jmeter_server_pass = "${params.jmeter_server_pass}"
        // BUILD_ROOT = "${params.BUILD_ROOT}"
        jmeter_server_username = "${params.jmeter_server_username}"
        application_server = "${params.application_server}"
        user_input_folder = "${params.user_input_folder}"
        PIPELINE_SCRIPTS_FOLDER = "${params.PIPELINE_SCRIPTS_FOLDER}"
        TODAY_DATE = sh(returnStdout: true, script: 'date +%d-%m-%Y').trim()
        GIT_CRED_JILE = credentials('git_cred_jile')
        nexus_host = '10.170.21.70'
        nexus_port = '8088' //Docker Nexus Login Port
        nexus_user = credentials('nexus')
        sonar_user = credentials('sonar_cred')
        PURPOSE = "${params.PURPOSE}"
        DEPLOY_TYPE = "${params.DEPLOY_TYPE}"
        TARGET_IP = "${params.TARGET_IP}"
        SCRIPTS_PATH = "${params.SCRIPTS_PATH}"
        DEPLOYABLE_PATH = "${params.DEPLOYABLE_PATH}"
        COMPONENT_NAME = "${params.COMPONENT_NAME}"
        USER_TOKEN = "114eb62fa7095604ac6837cabe20296a93"
        USER = "ttguser"
        SVN_CRED = credentials('svn_cred')
        CRAWL_FILE = "${params.CRAWL_FILE}"
        SCM_REVISION_NUMBER = "${SCM_REVISION_NUMBER}"
        SCM_METHOD = "${params.SCM_METHOD}"
    }
    parameters
    {
        string(defaultValue: 'http://172.18.228.57:8091', description: 'Application URL', name: 'inputApiBaseURL')
        string(defaultValue: 'http://172.18.228.57:8079', description: 'Application URL2', name: 'inputApiBaseURL2')
        booleanParam(defaultValue: false, description: 'Enable to create each defect for each sast issue', name: 'devplusDefectForSecIssues')
        string(defaultValue: 'http://172.18.228.220:8080/users_group/jile_mc_devsecops_scripts.git', description: 'URL for the SCM script', name: 'SCM_URL_SCRIPT')
        string(defaultValue: 'http://172.18.228.220:8080/root/jilescripts.git', description: 'URL for the SCM_NEW SCRIPT', name: 'SCM_URL_NEW')
        //string(defaultValue: 'http://172.18.228.57:8081/job/SAST-demo/buildWithParameters', description: 'URL for Jenkins Queue server', name: 'FORTIFY_JENKINS_QUEUE_SERVER')
        string(defaultValue: 'http://172.18.228.220:8080/root/crawlfiles.git', description: 'Git checkout crawl', name: 'CRAWL_CHECKOUT')
        string(defaultValue: 'http://172.18.129.234:8083/job/TTG_DAST_Multiple_SCM_Scanning/buildWithParameters/', name: 'WEBINSPECT_JENKINS_QUEUE_SERVER')
        string(defaultValue: 'http://172.18.129.234:8083', name: 'WEBINSPECT_JENKINS_QUEUE_SERVER_NEW')
        string(defaultValue: 'http://172.18.129.234:8083/job/TTG_DAST_Multiple_SCM_Scanning', name: 'WEBINSPECT_JENKINS_QUEUE_SERVER_NEW1')
        string(defaultValue: 'Manual_File_Upload', name: 'SCM_METHOD')
        string(defaultValue: 'https://sso.dev.nonprod.jile.tcsapps.com/auth/t-febnew20/login?client_id=app&state=/login&regionCode=US&langCode=en', name: 'APPLICATION_URL_DAST')
        string(name: 'job1', defaultValue: 'Third_Party Utility', description: 'Branch name for Third-Party Utility')
        string(name: 'job2', defaultValue: 'ImageVerify Utility', description: 'Branch name for Image Plagiarism/ImageVerify Utility')
        string(name: 'job3', defaultValue: 'OSS Scan', description: 'Branch name for OSS Scan')
        // Keeps Image Plagiarism + Nexus skipping rule
        choice(
            name: 'RECERTIFICATION',
            choices: ['NO', 'YES'],
            description: 'If YES, skip Image Plagiarism and Nexus'
        )
        // === NEW: Combination skip controls ===
        booleanParam(name: 'SKIP_ALL', defaultValue: false, description: 'Skip CVC, OSS and SAST groups')
        booleanParam(name: 'SKIP_CVC', defaultValue: false, description: 'Skip CVC group')
        booleanParam(name: 'SKIP_OSS', defaultValue: false, description: 'Skip OSS group')
        booleanParam(name: 'SKIP_SAST', defaultValue: false, description: 'Skip SAST group')
    }
    agent {
        label 'master'
    }
    stages
    {
        stage('Pre-checks') {
            steps {
                script {
                    // Load properties from the config file
                    def configFilePath = '/var/jenkins_home/Softwares/config.properties'
                    def props = [:]
                    // Check if the configuration file exists
                    if (fileExists(configFilePath)) {
                        props = readProperties file: configFilePath
                    } else {
                        error("Configuration file not found: ${configFilePath}")
                    }
                    // Retrieve nodes and Nexus configuration from properties
                    def nodesToCheck = props.get('nodes').split(',')
                    def nexus_host = props.get('nexus_host')
                    def nexus_port = props.get('nexus_port')
                    // Retrieve credentials from properties
                    def githubUser = props.get('github_user')
                    def githubToken = props.get('github_token')
                    def dastUser = props.get('dast_user')
                    def dastToken = props.get('dast_token')
                    def fortifyUser = props.get('fortify_user')
                    def fortifyToken = props.get('fortify_token')
                    def sonarUser = props.get('sonar_user')
                    def sonarPassword = props.get('sonar_password')
                    // List of parameters to check
                    def paramsToCheck = [
                        'BUILD_TYPE',
                        'PROJECT',
                        'BUILD_ROOT',
                        'NFS_SHARE',
                        'SCM_URL',
                        'BUILD_SERVER_IP',
                        'BUILD_SCRIPT',
                        'CHECKOUT_SCRIPT',
                        'PIPELINE_SCRIPTS_FOLDER',
                        'JENKINS_MOUNT',
                        'COMPONENT_LIST',
                        'TIPAR_ID',
                        'PIPELINE',
                        'FOLDER',
                        'REMOTE_DIR',
                        'VERSION',
                        'FORTIFY_SSC_SERVER_IP',
                        //'FORTIFY_JENKINS_QUEUE_SERVER',
                        //'inputApiBaseURL'
                    ]
                    // Check for empty parameters
                    def emptyParams = []
                    for (param in paramsToCheck) {
                        if (!params[param]) {
                            emptyParams.add(param)
                            echo "Parameter ${param} is empty."
                        }
                    }
                    // If there are empty parameters, fail the build
                    if (emptyParams) {
                        error("Pre-check failed: The following parameters are empty: ${emptyParams.join(', ')}")
                    }
                    // Display all parameters passed to the pipeline
                    echo "Parameters passed to the pipeline:"
                    for (param in paramsToCheck) {
                        echo "${param}: ${params[param]}"
                    }
                    // Test SSH connection using sshPublisher
                    try {
                        sshPublisher(publishers: [sshPublisherDesc(
                            configName: "${params.BUILD_SERVER_IP}",
                            transfers: [sshTransfer(execCommand: "echo SSH connection successful")],
                            verbose: true
                        )])
                        echo "SSH connection successful."
                    } catch (Exception e) {
                        error("SSH connection failed: ${e.getMessage()}")
                    }
                    sshPublisher(publishers: [sshPublisherDesc(
                        configName: "${params.BUILD_SERVER_IP}",
                        transfers: [sshTransfer(execCommand: """
                            if [ -f $NFS_SHARE/$CHECKOUT_SCRIPT ]; then
                                echo "File $NFS_SHARE/$CHECKOUT_SCRIPT exists."
                            else
                                echo "File $NFS_SHARE/$CHECKOUT_SCRIPT does not exist."
                            fi
                        """)],
                        verbose: true
                    )])
                    // Check SAST and DAST services
                    def servers = [
                        [name: 'DASTServer', url: 'http://172.18.129.234:8083', user: dastUser, token: dastToken],
[name: 'Fortify', url: 'http://172.18.228.112:8080/ssc/html/login/', user: fortifyUser, token: fortifyToken]
                    ]
                    // Loop through each server and check accessibility
                    servers.each {
                        server -> try {
                            def response = sh(script: "curl -s -o /dev/null -w '%{http_code}' -u ${server.user}:${server.token} ${server.url}", returnStdout: true).trim()
                            if (response == '200') {
                                echo "${server.name} is accessible."
                            } else {
                                echo "${server.name} is not accessible. HTTP response code: ${response}"
                            }
                        } catch (Exception e) {
                            echo "${server.name} is not accessible. Error: ${e.getMessage()}"
                        }
                    }
                    // Check Nexus accessibility
                    try {
                        def nexusResponse = sh(script: "curl -u ${props.nexus_user}:${props.nexus_password} -I http://${nexus_host}:${nexus_port}/service/rest/v1/status", returnStdout: true).trim()
                        if (nexusResponse.contains('200 OK')) {
                            echo "Nexus is accessible."
                        } else {
                            echo "Nexus is not accessible. HTTP response: ${nexusResponse}"
                        }
                    } catch (Exception e) {
                        echo "Error checking Nexus accessibility: ${e.getMessage()}"
                    }
                    // Check SonarQube accessibility
                    try {
                        def sonarResponse = sh(script: "curl -s -o /dev/null -w '%{http_code}' -u ${sonarUser}:${sonarPassword} http://172.18.228.57:8082", returnStdout: true).trim()
                        if (sonarResponse == '200') {
                            echo "SonarQube is accessible."
                        } else {
                            echo "SonarQube is not accessible. HTTP response code: ${sonarResponse}"
                        }
                    } catch (Exception e) {
                        echo "Error checking SonarQube accessibility: ${e.getMessage()}"
                    }
                    // Define the Jenkins URL, username, and API token
                    def jenkinsUrl = 'http://172.18.228.57:8081'
                    def username = props.jenkins_user // Use GitHub username from properties
                    def apiToken = props.jenkins_token // Use GitHub token from properties
                    // List of nodes to check
                    //def nodesToCheck = ['cvcnode', 'nfs_sast_node', 'sonar_node', 'ttgsastnode', 'postgres', 'ossnode']
                    // Iterate over each node and check its status
                    for (nodeName in nodesToCheck) {
                        // Execute the curl command to check the node status
                        def response = sh(script: "curl -s -u ${username}:${apiToken} ${jenkinsUrl}/computer/${nodeName}/api/json", returnStdout: true).trim()
                        // Parse the JSON response using Groovy's JsonSlurper
                        def jsonResponse = new groovy.json.JsonSlurper().parseText(response)
                        // Check if the node is offline
                        if (jsonResponse.offline) {
                            echo "Node '${nodeName}' is offline."
                        } else {
                            echo "Node '${nodeName}' is online."
                        }
                    }
                    echo "All pre-checks passed successfully."
                }
            }
        }
        stage('Checkout') {
            steps {
                echo 'Checkout Stage'
                script {
                    // --- Checkout logic ---
                    if ("${params.BUILD_TYPE}" == "incremental" && "${params.PROJECT}" == "Jile_Insights") {
                        echo "RUN THE INCREMENTAL CHECKOUT FOR ${params.PROJECT} PROJECT (with BRANCH)..."
                        sshPublisher(
                            publishers: [
                                sshPublisherDesc(
                                    configName: "${params.BUILD_SERVER_IP}",
                                    transfers: [
                                        sshTransfer(
                                            execCommand: "sh $NFS_SHARE/$CHECKOUT_SCRIPT_INCR '${params.BUILD_ROOT}' '${params.PROJECT}' '${params.VERSION}' '${params.SCM_URL}' $GIT_CRED_USR $GIT_CRED_PSW "
                                        )
                                    ],
                                    verbose: true
                                )
                            ]
                        )
                    } else if ("${params.BUILD_TYPE}" == "incremental") {
                        echo "RUN THE INCREMENTAL CHECKOUT FOR ${params.PROJECT} PROJECT..."
                        sshPublisher(
                            publishers: [
                                sshPublisherDesc(
                                    configName: "${params.BUILD_SERVER_IP}",
                                    transfers: [
                                        sshTransfer(
                                            execCommand: "sh $NFS_SHARE/$CHECKOUT_SCRIPT_INCR '${params.BUILD_ROOT}' '${params.PROJECT}' '${params.VERSION}' '${params.SCM_URL}' $GIT_CRED_USR $GIT_CRED_PSW "
                                        )
                                    ],
                                    verbose: true
                                )
                            ]
                        )
                    } else {
                        echo "RUN THE NORMAL CHECKOUT FOR ${params.PROJECT} PROJECT..."
                        sshPublisher(
                            publishers: [
                                sshPublisherDesc(
                                    configName: "${params.BUILD_SERVER_IP}",
                                    transfers: [
                                        sshTransfer(
                                            execCommand: "sh $NFS_SHARE/$CHECKOUT_SCRIPT '${params.BUILD_ROOT}' '${params.PROJECT}' '${params.VERSION}' '${params.SCM_URL}' $GIT_CRED_USR $GIT_CRED_PSW "
                                        )
                                    ],
                                    verbose: true
                                )
                            ]
                        )
                    }
                    // --- Post-checkout folder setup ---
                    if (fileExists('all_folders')) {
                        echo 'The directory exists.'
                        sh 'ls -ld all_folders'
                    } else {
                        // Assumes TIPAR_ID is already exported/available in the environment
                        sh """
                    mkdir -p all_folders && cd all_folders && \
                    mkdir -p CVC SAST DAST OSS "${TIPAR_ID}" && \
                    cp -r "${TIPAR_ID}" CVC && \
                    cp -r "${TIPAR_ID}" OSS && \
                    cp -r "${TIPAR_ID}" SAST && \
                    cp -r "${TIPAR_ID}" DAST && \
                    rm -rf "${TIPAR_ID}"
                """
                        sh "chmod +x all_folders"
                    }
                }
            }
        }
        stage('Build') {
            steps {
                echo 'Build Stage'
                script {
                    echo "THE BUILD TYPE IS ........... ${params.BUILD_TYPE}...................."
                    if ("${params.BUILD_TYPE}" == "incremental") {
                        def buildParams = [
                            string(name: 'PROJECT', value: "${params.PROJECT}"),
                            string(name: 'BUILD_TYPE', value: "${params.BUILD_TYPE}"),
                            string(name: 'VERSION', value: "${params.VERSION}"),
                            string(name: 'SCM_URL', value: "${params.SCM_URL}"),
                            string(name: 'BUILD_ROOT', value: "${params.BUILD_ROOT}"),
                            string(name: 'NFS_SHARE', value: "${params.NFS_SHARE}"),
                            string(name: 'BUILD_SERVER_IP', value: "${params.BUILD_SERVER_IP}"),
                            string(name: 'PIPELINE_SCRIPTS_FOLDER', value: "${params.PIPELINE_SCRIPTS_FOLDER}"),
                            string(name: 'COMPONENT_LIST', value: "${params.COMPONENT_LIST}")
                        ]
                        if ("${params.PROJECT}" == "REE") {
                            echo "RUN THE INCREMENTAL BUILD FOR THAT ${params.PROJECT} PROJECT..."
                            // buildParams.add(string(name: 'BUILD_SCRIPT_INCR', value: "${params.BUILD_SCRIPT_INCR}"))
                            sshPublisher(publishers: [sshPublisherDesc(configName: "${params.BUILD_SERVER_IP}", transfers: [sshTransfer(execCommand: "sh $NFS_SHARE/$BUILD_SCRIPT_INCR '${params.BUILD_ROOT}' '${params.PROJECT}' '${params.VERSION}' '${params.SCM_URL}' $GIT_CRED_USR $GIT_CRED_PSW ")], verbose: true)])
                        }
                        build job: 'BuildPipelineIncremental', parameters: buildParams
                    } else {
                        echo 'RUN THE NORMAL BUILD FOR THAT ${params.PROJECT} PROJECT...'
                        markBuild pipelineType: 'CI,FT'
                        echo "APPLN_RELEASE_NUM:7.0"
                        echo "APPLN_LIFECYCLE_ID=:${currentBuild.number}"
                        echo "Starting building: dashboard #${currentBuild.number}"
                        echo 'I am going to call the build script'
                        echo "${params.BUILD_SERVER_IP}"
                        try  {
                            sshPublisher(publishers: [sshPublisherDesc(configName: "${params.BUILD_SERVER_IP}", transfers: [sshTransfer(execCommand: "sh '${params.BUILD_ROOT}/${params.BUILD_SCRIPT}' '${params.BUILD_ROOT}' '${params.PROJECT}' '${params.VERSION}' '${params.SCM_URL}' '${params.BUILD_TYPE}' '${params.NFS_SHARE}'")], verbose: true)])
                        } catch (Exception e) {
                            echo "sshPublisher step failed: ${e.getMessage()}"
                            error('Remote build failed. Aborting execution')
                        }
                        echo 'End of groovy- build stage'
                        echo 'Junit started!'
                        echo " ${JENKINS_NFS_MOUNT}/${params.PROJECT}/${VERSION}"
                        echo "${params.PROJECT}"
                        if ("${params.PROJECT}" == "Jile_Insights" || "${params.PROJECT}" == "IPSafePackUtilities") {
                            junit (allowEmptyResults: true, testResults: '$JENKINS_NFS_MOUNT/${params.PROJECT}/${VERSION}/**/target/surefire-reports/*.xml', testDataPublishers: [cdpResultReporter()])			//publish coverage
                            publishCoverage adapters: [jacocoAdapter('$JENKINS_NFS_MOUNT/${params.PROJECT}/${SCM_BRANCH}/**/target/jacoco/*.xml')], sourceFileResolver: sourceFiles('NEVER_STORE')
                        } else {
                            echo 'no unit test cases'
                        }
                    }
                }
            }
        }
        stage('Starting NFR Activities') {
            parallel {
                stage('CVC') {
                    // Skip whole CVC group if SKIP_ALL or SKIP_CVC
                    when {
                        beforeAgent true
                        expression {
                            !(params.SKIP_ALL || params.SKIP_CVC)
                        }
                    }
                    stages {
                        stage('Starting CVC') {
                            agent {
                                label 'cvcnode'
                            }
                            steps {
                                script {
                                    //load "$WORKSPACE/Groovy_scripts/m_cvc_build.groovy"
                                    load "$WORKSPACE/Groovy_scripts/m_cvc_jile_insights.groovy"
                                    sh "echo $WORKSPACE"
                                    dir("/var/jenkins_home/jobs/${PIPELINE}/workspace") {
                                        def reportpath = "all_folders/CVC/${params.TIPAR_ID}/dependency-check-PrevReport.xlsx"
                                        archiveArtifacts allowEmptyArchive: true, artifacts: reportpath, onlyIfSuccessful: true
                                    }
                                }
                            }
                        }
                        stage('CVC Utility') {
                            steps {
                                script {
                                    echo 'Running CVC utility...'
                                    //archiveArtifacts allowEmptyArchive: true, artifacts: "dependency-check-report.xml", followSymlinks: false
                                }
                            }
                        }
                    }
                }
                stage('OSS') {
                    // Skip whole OSS group if SKIP_ALL or SKIP_OSS
                    when {
                        beforeAgent true
                        expression {
                            !(params.SKIP_ALL || params.SKIP_OSS)
                        }
                    }
                    stages {
                        stage('OSS tasks in parallel') {
                            steps {
                                script {
                                    // Keep: recertification control for Image Plagiarism branch
                                    def isRecert = (params.RECERTIFICATION ?: 'NO').trim().toUpperCase() 
                                        == 'YES'
                                    def branches = [:]
                                    // Third_Party Utility (job1) - always run
                                    branches[params.job1] = {
                                        echo "Starting: ${params.job1}"
                                        load "$WORKSPACE/Groovy_scripts/m_Third_party_utility.groovy"
                                        sh "echo $WORKSPACE"
                                        // sh " cp all_folders/OSS/TIPAR-112025-000045/jile_insights_7.0/Code/Keycloak/User_Sync/*.xlsx  /var/jenkins_home/jobs/${PIPELINE}/workspace/all_folders/OSS/${params.TIPAR_ID}"
                                        //  sh " cp all_folders/OSS/TIPAR-112025-000045/jile_insights_7.0/Code/RestServices/*.xlsx  /var/jenkins_home/jobs/${PIPELINE}/workspace/all_folders/OSS/${params.TIPAR_ID}"
                                        dir("/var/jenkins_home/jobs/${PIPELINE}/workspace") {
                                            def reportpath = "all_folders/OSS/${params.TIPAR_ID}/*.xlsx"
                                            archiveArtifacts allowEmptyArchive: true, artifacts: reportpath, onlyIfSuccessful: true
                                        }
                                    }
                                    // ImageVerify Utility (job2) - skip if RECERTIFICATION == YES
                                    if (!isRecert) {
                                        branches[params.job2] = {
                                            echo "Starting: ${params.job2}"
                                            load "$WORKSPACE/Groovy_scripts/m_Image_Plagiarism.groovy"
                                        }
                                    } else {
                                        echo "Skipping ${params.job2} (Image Plagiarism) because RECERTIFICATION=YES"
                                    }
                                    // OSS Scan (job3) - always run
                                    branches[params.job3] = {
                                        echo "Starting: ${params.job3}"
                                        load "$WORKSPACE/Groovy_scripts/m_oss_v1.groovy"
                                    }
                                    // Execute selected branches in parallel
                                    parallel branches
                                }
                            }
                        }
                        // Sequential after parallel
                        stage('OSS Utility') {
                            steps {
                                script {
                                    echo "hi"
                                    load "$WORKSPACE/Groovy_scripts/m_oss_scan_dashboad.groovy"
                                }
                            }
                        }
                        stage('OSS Inventory') {
                            steps {
                                script {
                                    echo "hi"
                                    //load "$WORKSPACE/Groovy_scripts/m_Call_Inventory.groovy"
                                }
                            }
                        }
                    }
                }
                stage('SAST') {
                    when {
                        beforeAgent true
                        expression {
                            !(params.SKIP_ALL || params.SKIP_SAST)
                        }
                    }
                    stages {
                        stage('SAST Scan') {
                            steps {
                                echo 'Running SAST Scan...'
                                script {
                                    load "$WORKSPACE/Groovy_scripts/m_sast_v1.groovy"
                                    // load "$WORKSPACE/Groovy_scripts/m_SAST-demo_Incr.groovy"
                                    sh "echo $WORKSPACE"
                                    dir("/var/jenkins_home/jobs/${PIPELINE}/workspace") {
                                        def reportpath = "all_folders/SAST/${params.TIPAR_ID}/*.xlsx"
                                        archiveArtifacts allowEmptyArchive: true, artifacts: reportpath, onlyIfSuccessful: true
                                    }
                                }
                            }
                        }
                        stage('SAST_Utility') {
                            steps {
                                script {
                                    load "$WORKSPACE/Groovy_scripts/m_SAST_Utility.groovy"
                                }
                            }
                        }
                    }
                }
            }
        }
        //  stage('Nexus')
        //         {
        //             steps{
        //                 echo "Running Nexus........"
        //                 script{
        //                         load "$WORKSPACE/Groovy_scripts/m_Nexus.groovy"
        //                 }
        //             }
        //         }
        /* stage('Deploy')
                    {

                stages 
                    {
                        stage('Deploy'){
                            steps{
                                script{
                                    load "$WORKSPACE/Groovy_scripts/m_Deploy.groovy"
                                }
                            }
                        }
       
                     
                /*stage('FilesMovedToAzure'){
            
                agent{label 'linux_node'}
             
                steps{
                 
                 unstash 'deploy'
                }
            
                }*/
        // }
        // }
        /*  stage('Performance Pipeline') {
                            steps {
                                script{
                                
                                    load "$WORKSPACE/Groovy_scripts/m_PTJenkinsfile.groovy"
                     
                                }
                        
                            }
                        }*/
        //stage('DAST'){
        // stages{
        // stage('DAST Scan'){
        // steps{
        // script{
        // load "$WORKSPACE/Groovy_scripts/m_DAST.groovy"
        // }
        // }
        // }
        // stage('DAST Utility')
        //  {
        // steps{
        // script{
        // load "$WORKSPACE/Groovy_scripts/m_DAST_Utility.groovy"
        // }
        // }
        // }
        //  }
        // }
        /* stage('Selenium')
                    {

                     steps 
                        {
                            echo 'Regression testing'
                             build job: 'Jile_Selenium_new_ft'
                        }
                        
             //       }
        //  */
    }
    //post build actions based on the result of the pipeline
    post
    {
        failure
        {
            sh 'echo "Build Failed" '
            updateGitlabCommitStatus name: "Jenkins Build #$BUILD_NUM", state: 'failed'
        }
        success
        {
            sh 'echo "Build success" '
            updateGitlabCommitStatus name: "Jenkins Build #$BUILD_NUM", state: 'success'
        }
    }
}
