pipeline {
    // tools used in pipeline is mentioned in this section
    tools {
        maven "Maven_3.6"
        jdk "openjdk-8"
    }
    environment {
        BUILD_NUM = "${currentBuild.getNumber()}"
        TIPAR_ID = "${params.TIPAR_ID}"
        PROJECT = "${params.PROJECT}"
        JENKINS_MOUNT = "${JENKNINS_MOUNT}"
        VERSION = "${params.VERSION}"
        VERSION2 = "${params.VERSION2}"
        BUILD_TYPE = "${params.BUILD_TYPE}"
        NFS_SHARE = "${params.NFS_SHARE}"
        BUILD_SERVER_IP = "${BUILD_SERVER_IP}"
        FORTIFY_SSC_SERVER_IP = "${params.FORTIFY_SSC_SERVER_IP}"
        PIPELINE = "${params.PIPELINE}"
        APPLLICATION_VERSION_IN_SSC = "${params.APPLLICATION_VERSION_IN_SSC}"
        APPLLICATION_VERSION_IN_SSC_OLD = "${params.APPLLICATION_VERSION_IN_SSC_OLD}"
        SAST_NODE = "${params.SAST_NODE}"
        SAST_JENKINS_NODE = "${params.SAST_JENKINS_NODE}"
    }
    agent {
        label 'master'
    }
    stages {
        stage('Starting SAST') {
            steps {
                script {
                    //echo "============ copying audit data (dev comments) from previous application version in ssc portal to current application version in ssc portal ========="
                    //sh "java -jar /var/jenkins_home/Softwares/SAST/SSC_FPR_transfer_1.7_debug.jar -sscUrl 'http://172.18.228.112:8080/ssc' -userName admin -password Password@123456! -projectName1 ${params.PROJECT}_${params.APPLLICATION_VERSION_IN_SSC} -projectVersion1 ${params.PREV_VERSION} -projectName2 ${params.PROJECT}_${params.APPLLICATION_VERSION_IN_SSC} -projectVersion2 ${params.VERSION}"
                    def compList = "${params.COMPONENT_LIST}" ?: ""
                    def list = [:]
                    echo "COMPONENT_LIST:\n${params.COMPONENT_LIST}"
                    // Split lines, trim, and skip blanks
                    compList.split(/\r?\n/).findAll {
                        it?.trim()
                    }.each {
                        compLine -> def compOrig = compLine.trim()
                        println "Original component: ${compOrig}"
                        // 1) Windows path variant: '/' -> '\' then append \src
                        def compPathWin = compOrig.replaceAll('/', '\\\\')
                        def compPathWinSrc = compPathWin + '\\src'
                        println "Windows path (+src): ${compPathWinSrc}"

                        // 2) Underscore-safe name: replace both '/' and '\' with '_'
                        def compNameUnderscore = compOrig.replaceAll(/[
                            \/\\]/, '_')
                            println "Underscore name: ${compNameUnderscore}"
                            // 3) Base name from original
                            def baseName = new File(compOrig).getName()
                            println "Base name: ${baseName}"
                            // Make the parallel display key stable and unique
                            def displayKey = "Build ${compNameUnderscore}"
                            list[displayKey] = {
                                build job: 'TtgSast',
                                parameters: [
                                    string(name: 'FORTIFY_SSC_SERVER_IP', value: "${params.FORTIFY_SSC_SERVER_IP}"),
                                    string(name: 'PROJECT', value: "${params.PROJECT}"),
                                    string(name: 'VERSION', value: "${params.VERSION}"),
                                    string(name: 'VERSION2', value: "${params.VERSION2}"),
                                    string(name: 'COMPONENT', value: compPathWinSrc), // Windows-style path + \src
                                    string(name: 'BASE_NAME', value: baseName),
                                    string(name: 'TIPAR_ID', value: TIPAR_ID),
                                    string(name: 'PIPELINE', value: PIPELINE),
                                    string(name: 'BUILD_TYPE', value: "${params.BUILD_TYPE}"),
                                    string(name: 'APPLLICATION_VERSION_IN_SSC', value: compNameUnderscore), // underscore-safe variant
                                    string(name: 'APPLLICATION_VERSION_IN_SSC_OLD', value: "${params.APPLLICATION_VERSION_IN_SSC_OLD}"),
                                    string(name: 'SAST_NODE', value: "${params.SAST_NODE}"),
                                    string(name: 'SAST_JENKINS_NODE', value: "${params.SAST_JENKINS_NODE}")
                                ]
                            }
                            }
                            // Run all component builds in parallel
                            parallel list
                            }
                            }
                            }
                            }
                            // post build actions based on the result of the pipeline
                            post {
                                failure {
                                    sh 'echo "Build Failed" '
                                    updateGitlabCommitStatus name: "Jenkins Build #${BUILD_NUM}", state: 'failed'
                                }
                                success {
                                    sh 'echo "Build success" '
                                    updateGitlabCommitStatus name: "Jenkins Build #${BUILD_NUM}", state: 'success'
                                }
                            }
                            }
