pipeline {
    tools {
        maven "Maven_3.6"
        jdk "openjdk-8"
    }

    environment {
        WEBHOOK_TOKEN = "${params.WEBHOOK_TOKEN}"
        BUILD_NUM = "${currentBuild.getNumber()}"
    }

    parameters {
        string(defaultValue: 'dso_generic_receiver_token', description: '', name: 'WEBHOOK_TOKEN')
        string(defaultValue: ' /shared_files/Orchestrator/orchestration.yaml', description: '', name: 'ORCH_CONFIG_PATH')
        string(defaultValue: '/shared_files/Orchestrator/Projects/Insights_PLAC/Insights_PLAC_test_7.0.yaml', description: '', name: 'PROJECT_CONFIG_PATH')
        string(defaultValue: '/shared_files/Orchestrator/Tools/', description: '', name: 'TOOLS_CONFIG_PATH')
        string(defaultValue: '/shared_files/Orchestrator/Tools/CVC/dependency_check.yaml', description: '', name: 'CVC_CONFIG_PATH')
        string(defaultValue: '/shared_files/Orchestrator/Tools/OSS/palamida_oss.yaml', description: '', name: 'OSS_CONFIG_PATH')
        string(defaultValue: '/shared_files/Orchestrator/Tools/SAST/fortify_sast.yaml', description: '', name: 'SAST_CONFIG_PATH')
        string(defaultValue: '/shared_files/Orchestrator/Tools/DAST/Dast.yaml', description: '', name: 'DAST_CONFIG_PATH')
        string(defaultValue: '/shared_files/Orchestrator/async_incr_checkout.sh', description: '', name: 'CHECKOUT_SCRIPT')
        string(defaultValue: '172.18.228.115', description: '', name: 'BUILD_SERVER_IP')
        string(defaultValue: 'R:\\Jile\\Jile-ALM-7.2-dev\\jiledevplus\\Jile\\Agile\\Code\\Server\\target\\stest-0.0.1-SNAPSHOT.jar.original', description: '', name: 'COMP_JAR')
        string(defaultValue: 'dso_insights_plac', description: '', name: 'CHECKOUT_SUBDIR')

        choice(
            name: 'RECERTIFICATION',
            choices: ['NO', 'YES'],
            description: 'If YES, skip Image Plagiarism and Nexus'
        )

        booleanParam(name: 'SKIP_ALL', defaultValue: false, description: 'Skip CVC, OSS and SAST groups')
        booleanParam(name: 'SKIP_CVC', defaultValue: false, description: 'Skip CVC group')
        booleanParam(name: 'SKIP_OSS', defaultValue: false, description: 'Skip OSS group')
        booleanParam(name: 'SKIP_SAST', defaultValue: false, description: 'Skip SAST group')
    }
   
   options {
        skipDefaultCheckout(true)
    }

    agent {
        label 'master'
    }

    stages {
        stage('Pre-checks') {
            steps {
                script {
                    echo "Pre-checks stage"
                          def orchPath = params.ORCH_CONFIG_PATH?.trim()
            def toolsDir = params.TOOLS_CONFIG_PATH?.trim()

            echo "Reading orchestration YAML from: '${orchPath}'"
            echo "Tools directory: '${toolsDir}'"

            // Check orchestration YAML exists
            def orchStatus = sh(script: "test -f '${orchPath}'", returnStatus: true)
            if (orchStatus != 0) {
                error "File not found on agent: '${orchPath}'"
            }

            echo "----- Orchestration YAML contents reading starts --------"
            def yamlText = sh(script: "cat '${orchPath}'", returnStdout: true).trim()
            echo yamlText
            echo "----- Orchestration YAML contents reading ends --------"

            // ============================================================
            // DAST check
            // ============================================================
            def dastPath = params.DAST_CONFIG_PATH?.trim()
            def dastStatus = sh(script: "test -f '${dastPath}'", returnStatus: true)
            if (dastStatus != 0) {
                error "File not found on agent: '${dastPath}'"
            }

            def dastCfg = readYaml file: dastPath
            def dastUrl = dastCfg.server_url

            echo "DAST URL = ${dastUrl}"
            if (dastUrl) {
                def status = sh(
                    script: "curl -k -I --silent --connect-timeout 5 '${dastUrl}'",
                    returnStatus: true
                )

                if (status == 0) {
                    echo "DAST Connectivity OK"
                } else {
                    echo "DAST Connectivity FAILED. curl exit ${status}"
                }
            } else {
                echo "DAST URL is empty. Skipping DAST check."
            }

            // ============================================================
            // OSS check
            // ============================================================
            def ossPath = params.OSS_CONFIG_PATH?.trim()
            def ossStatus = sh(script: "test -f '${ossPath}'", returnStatus: true)
            if (ossStatus != 0) {
                error "File not found on agent: '${ossPath}'"
            }

            def ossCfg = readYaml file: ossPath
            def ossUrl = ossCfg.SERVER

            echo "OSS URL = ${ossUrl}"
            if (ossUrl) {
                def status = sh(
                    script: "curl -k -I --silent --connect-timeout 5 '${ossUrl}'",
                    returnStatus: true
                )

                if (status == 0) {
                    echo "OSS Connectivity OK"
                } else {
                    echo "OSS Connectivity FAILED. curl exit ${status}"
                }
            } else {
                echo "OSS URL is empty. Skipping OSS check."
            }

            // ============================================================
            // CVC check
            // ============================================================
            def cvcPath = params.CVC_CONFIG_PATH?.trim()
            def cvcStatus = sh(script: "test -f '${cvcPath}'", returnStatus: true)
            if (cvcStatus != 0) {
                error "File not found on agent: '${cvcPath}'"
            }

            def cvcCfg = readYaml file: cvcPath
            def cvcUrl = cvcCfg.server_url

            echo "CVC URL = ${cvcUrl}"
            if (cvcUrl) {
                def status = sh(
                    script: "curl -k -I --silent --connect-timeout 5 '${cvcUrl}'",
                    returnStatus: true
                )

                if (status == 0) {
                    echo "CVC Connectivity OK"
                } else {
                    echo "CVC Connectivity FAILED. curl exit ${status}"
                }
            } else {
                echo "CVC URL is empty. Skipping CVC check."
            }

            // ============================================================
            // SAST check
            // ============================================================
            def sastPath = params.SAST_CONFIG_PATH?.trim()
            def sastStatus = sh(script: "test -f '${sastPath}'", returnStatus: true)
            if (sastStatus != 0) {
                error "File not found on agent: '${sastPath}'"
            }

            def sastCfg = readYaml file: sastPath
            def sastUrl = sastCfg.server_url

            echo "SAST URL = ${sastUrl}"
            if (sastUrl) {
                def status = sh(
                    script: "curl -k -I --silent --connect-timeout 5 '${sastUrl}'",
                    returnStatus: true
                )

                if (status == 0) {
                    echo "SAST Connectivity OK"
                } else {
                    echo "SAST Connectivity FAILED. curl exit ${status}"
                }
            } else {
                echo "SAST URL is empty. Skipping SAST check."
            }

            // ============================================================
            // NEXUS check
            // ============================================================
            def nexusPath = "${toolsDir}/nexus.yaml"
            def nexusStatus = sh(script: "test -f '${nexusPath}'", returnStatus: true)
            if (nexusStatus != 0) {
                error "File not found on agent: '${nexusPath}'"
            }

            def nexusCfg = readYaml file: nexusPath
            def nexusProtocol = nexusCfg.protocol
            def nexusHost = nexusCfg.host
            def nexusPort = nexusCfg.port
            def nexusPathValue = nexusCfg.path ?: "/"

            if (!nexusPathValue.startsWith("/")) {
                nexusPathValue = "/" + nexusPathValue
            }

            def nexusUrl = "${nexusProtocol}://${nexusHost}:${nexusPort}${nexusPathValue}"
            def nexusUser = nexusCfg.user
            def nexusPass = nexusCfg.password

            echo "NEXUS URL = ${nexusUrl}"

            def nexusHttp = sh(
                script: """
                    set +x
                    curl -k --silent --connect-timeout 5 --max-time 15 -u '${nexusUser}:${nexusPass}' -o /dev/null -w '%{http_code}' '${nexusUrl}' || true
                """,
                returnStdout: true
            ).trim()

            if (!nexusHttp) {
                nexusHttp = "000"
            }

            echo "NEXUS HTTP Code = ${nexusHttp}"

            def nexusCode = 0
            try {
                nexusCode = Integer.parseInt(nexusHttp)
            } catch (e) {
                nexusCode = 0
            }

            if (nexusCode >= 200 && nexusCode < 400) {
                echo "NEXUS Connectivity OK"
            } else if (nexusHttp == "000") {
                echo "NEXUS Connectivity FAILED. Not reachable / connect failed."
            } else {
                echo "NEXUS Connectivity FAILED. HTTP ${nexusHttp}"
            }

            // ============================================================
            // JENKINS check
            // ============================================================
            def jenkinsPath = "${toolsDir}/jenkins.yaml"
            def jenkinsStatus = sh(script: "test -f '${jenkinsPath}'", returnStatus: true)
            if (jenkinsStatus != 0) {
                error "File not found on agent: '${jenkinsPath}'"
            }

            def jenkinsCfg = readYaml file: jenkinsPath
            def jenkinsProtocol = jenkinsCfg.protocol
            def jenkinsHost = jenkinsCfg.host
            def jenkinsPort = jenkinsCfg.port
            def jenkinsPathValue = jenkinsCfg.path ?: "/"

            if (!jenkinsPathValue.startsWith("/")) {
                jenkinsPathValue = "/" + jenkinsPathValue
            }

            def jenkinsUrl = "${jenkinsProtocol}://${jenkinsHost}:${jenkinsPort}${jenkinsPathValue}"
            def jenkinsCredId = jenkinsCfg.user_credential

            echo "JENKINS URL = ${jenkinsUrl}"
            echo "JENKINS CredentialId = ${jenkinsCredId}"

            withCredentials([usernamePassword(
                credentialsId: jenkinsCredId,
                usernameVariable: 'JENKINS_USR',
                passwordVariable: 'JENKINS_PSW'
            )]) {
                withEnv(["JENKINS_URL=${jenkinsUrl}"]) {
                    def jenkinsHttp = sh(
                        script: '''
                            set +x
                            code=$(curl -k --silent --connect-timeout 5 --max-time 15 -u "$JENKINS_USR:$JENKINS_PSW" -o /dev/null -w "%{http_code}" "$JENKINS_URL" || true)
                            [ -z "$code" ] && code="000"
                            echo "$code"
                        ''',
                        returnStdout: true
                    ).trim()

                    echo "JENKINS HTTP Code = ${jenkinsHttp}"

                    def jenkinsCode = 0
                    try {
                        jenkinsCode = Integer.parseInt(jenkinsHttp)
                    } catch (ignored) {
                        jenkinsCode = 0
                    }

                    if (jenkinsCode >= 200 && jenkinsCode < 400) {
                        echo "JENKINS Connectivity OK"
                    } else if (jenkinsHttp == "000") {
                        echo "JENKINS Connectivity FAILED. Not reachable / connect failed."
                    } else {
                        echo "JENKINS Connectivity FAILED. HTTP ${jenkinsHttp}"
                    }
                }
            }

            echo "All tool endpoints verified."
                }
            }
        }

        stage('Checkout') {
            steps {
                script {
                    echo "Checkout stage"
                    def projectPath = params.PROJECT_CONFIG_PATH?.trim()
            def scriptPath = params.CHECKOUT_SCRIPT?.trim()
            def yamlText = sh(
                script: "cat '${projectPath}'",
                returnStdout: true
            ).trim()
            def cfg = readYaml text: yamlText
['projectname', 'scm_branch', 'scm_url', 'root_workspace_dir'].each {
                k -> if (!cfg[k]) {
                    error "Missing key '${k}' in ${projectPath}"
                }
            }
            sh "cp '${scriptPath}' ./checkout.sh"
            sh 'command -v dos2unix >/dev/null 2>&1 && dos2unix ./checkout.sh || true'
            def computedVersion = (
                params.VERSION?.trim()
 ?: (cfg.scm_branch as String).replaceAll('/', '_')
 ?: env.BUILD_NUMBER
            )
            def incrBase = (
                cfg.incremental_scanpath
 ? cfg.incremental_scanpath.toString().trim()
: "${cfg.root_workspace_dir}/${cfg.projectname}/incremental"
            )
            def sharedBase = (
                cfg.shared_base
 ? cfg.shared_base.toString().trim()
: "/shared_files"
            )
            def checkoutSubdir = (
                cfg.checkout_subdir
 ? cfg.checkout_subdir.toString().trim()
: params.CHECKOUT_SUBDIR?.trim()
            )
            withCredentials([usernamePassword(
                credentialsId: 'git_cred',
                usernameVariable: 'GIT_USER',
                passwordVariable: 'GIT_PASS'
            )]) {
                withEnv([
                    "BUILD_ROOT=${sharedBase}",
                    "PROJECT_NAME=${cfg.projectname}",
                    "VERSION=${computedVersion}",
                    "FOURTH_ARG=${cfg.scm_url}",
                    "BRANCH=${cfg.scm_branch}",
                    "CHECKOUT_SUBDIR=${checkoutSubdir}",
                    "INCR_BASE=${incrBase}",
                    "SHARED_BASE=${sharedBase}",
                    "BUILD_NUMBER=${env.BUILD_NUMBER}"
                ]) {
                    sh '''#!/usr/bin/env bash
                    set -euo pipefail

                    chmod +x ./checkout.sh || true

                    echo "=== incr_checkout invocation ==="
                    echo "BUILD_ROOT      = $BUILD_ROOT"
                    echo "PROJECT_NAME    = $PROJECT_NAME"
                    echo "VERSION         = $VERSION"
                    echo "FOURTH_ARG      = $FOURTH_ARG"
                    echo "CHECKOUT_SUBDIR = $CHECKOUT_SUBDIR"
                    echo "BRANCH          = $BRANCH"
                    echo "INCR_BASE       = $INCR_BASE"
                    echo "SHARED_BASE     = $SHARED_BASE"
                    echo "BUILD_NUMBER    = $BUILD_NUMBER"
                    echo "================================"

                    bash ./checkout.sh "$BUILD_ROOT" "$PROJECT_NAME" "$VERSION" "$FOURTH_ARG" "$GIT_USER" "$GIT_PASS" "$CHECKOUT_SUBDIR"

                    echo "Checkout completed successfully."
                    echo "===================="
                    '''
                }
            }
                }
            }
        } //Checkout stage end

stage("Build") {
    steps {
        script {
            echo "Hello from Build stage"

            // 1) Read YAML config
            def projectPath = params.PROJECT_CONFIG_PATH?.trim()
            echo "Reading project config from: ${projectPath}"

            def yamlText = sh(script: "cat '${projectPath}'", returnStdout: true).trim()
            def cfg = readYaml text: yamlText

            // 2) Use params.BUILD_TYPE if provided, else YAML build_type
            def buildTypeVal = (params.BUILD_TYPE?.trim()) ?: (cfg.build_type?.toString()?.trim())
            def buildId = "${env.BUILD_NUM}"

            // 3) If incremental, build the downstream params from YAML
            if (buildTypeVal == "incremental") {

                def buildParams = [
                    string(name: 'NFS_SHARE',       value: "${cfg.shared_base ?: ''}"),
                    string(name: 'PROJECT',         value: "${cfg.projectname ?: ''}"),
                    string(name: 'VERSION',         value: "${cfg.scm_branch ?: ''}"),
                    string(name: 'BUILD_TYPE',      value: "${cfg.build_type ?: ''}"),
                    string(name: 'SCM_URL',         value: "${cfg.scm_url ?: ''}"),
                    string(name: 'BUILD_ROOT',      value: "${cfg.build_root ?: ''}"),

                    // NOTE: NFS_SHARE is already defined above.
                    // If this is intentional, keep it. Otherwise rename/remove one.
                    string(name: 'NFS_SHARE',       value: "${cfg.root_workspace_dir ?: ''}"),

                    string(name: 'BUILD_SERVER_IP', value: "${cfg.build_server_ip ?: ''}"),
                    // string(name: 'BUILD_ID',      value: "${buildId}"),

                    // As requested: leave this coming from params
                    string(name: 'COMPONENT_LIST',  value: "${params.COMPONENT_LIST ?: ''}")
                ]

                echo "Add build script here:"

                sshPublisher(
                    publishers: [
                        sshPublisherDesc(
                            configName: "${cfg.build_server_ip ?: ''}",
                            transfers: [
                                sshTransfer(
                                    execCommand: """
                                    echo "======================================="
                                        echo "Fixing ownership before execution"
                                        echo "======================================="

                                        TARGET_DIR="${cfg.root_workspace_dir ?: ''}/${cfg.projectname ?: ''}/${cfg.scm_branch ?: ''}"

                                        echo "Target directory: \$TARGET_DIR"

                                        if [ -d "\$TARGET_DIR" ]; then
                                            echo "Directory exists. Applying chown..."

                                            sudo chown -R sa24459371forti:sa24459371forti "\$TARGET_DIR"

                                            if [ \$? -ne 0 ]; then
                                                echo "WARNING: chown failed!"
                                            else
                                                echo "Ownership updated successfully"
                                            fi
                                        else
                                            echo "WARNING: Target directory not found!"
                                        fi

                                        echo "======================================="
                                        echo "Starting Incremental Build Script"
                                        echo "======================================="
                                        
                                        bash "${params.BUILD_SCRIPT_INCR}" \
                                        "${cfg.shared_base ?: ''}" \
                                        "${cfg.projectname ?: ''}" \
                                        "${cfg.scm_branch ?: ''}" \
                                        "${params.CHECKOUT_SUBDIR}" \
                                        "${cfg.root_workspace_dir ?: ''}" \
                                        "${params.COMPONENT_LIST ?: ''}"
                                    """
                                )
                            ],
                            verbose: true
                        )
                    ]
                )
            }
        }
    }
}//Build stage end 

stage("Starting NFR Activities") {
    parallel {
        stage("CVC") {
            //agent{label 'cvcnode'}
            steps {
                script {
                    echo "CVC stage started"
                    // Read parameters
                    def projconfigPath = params.PROJECT_CONFIG_PATH?.trim()
                    def toolconfigPath = params.TOOLS_CONFIG_PATH?.trim()
                    def orchconfigPath = params.ORCH_CONFIG_PATH?.trim()
                    def cvcConfigPath = params.CVC_CONFIG_PATH
                    def webhook_token = params.WEBHOOK_TOKEN
                    echo "Reading orchestration YAML from: '${projconfigPath}'"
                    // Read YAML content
                    def yamlText = sh(
                        script: "cat '${projconfigPath}'",
                        returnStdout: true
                    ).trim()
                    def cfg = readYaml text: yamlText
                    // Extract values
                    def projectname = cfg.projectname
                    def sourcepath = cfg.sourcepath
                    def scanpath = cfg.scanpath
                    def outputfolder = cfg.outputfolder
                    def web_url = cfg.web_url
                    def buildId = "${env.BUILD_NUM}"
                    echo "----- Orchestration YAML contents --------"
                    echo yamlText
                    echo "projectname        : ${projectname}"
                    echo "sourcepath         : ${sourcepath}"
                    echo "scanpath           : ${scanpath}"
                    echo "incremental_scanpath: ${cfg.incremental_scanpath}"
                    echo "outputfolder       : ${outputfolder}"
                    echo "data               : ${cfg.data}"
                    echo "web_url            : ${web_url}"
                    echo "CVC_CONFIG_PATH    : ${cvcConfigPath}"
                    echo "WEBHOOK_TOKEN      : ${webhook_token}"
                    echo "toolconfigPath     : ${toolconfigPath}"
                    echo "Build ID           : ${buildId}"
                    // Read orchestration config
                    def ocp = readYaml file: orchconfigPath
                    def buildType = ocp?.default_build_type ?: ""
                    echo "default_build_type = ${buildType}"
                    // Handle incremental build
                    if (buildType == "incremental") {
                        def incrementalScanPath = "/shared_files/${projectname}/incremental/incrjarsfoldernew"
                        // Check folder existence
                        def exists = sh(
                            script: "[ -d '${incrementalScanPath}' ] && echo yes || echo no",
                            returnStdout: true
                        ).trim() == "yes"
                        if (!exists) {
                            echo "Folder not present: ${incrementalScanPath}"
                            echo "Skipping CVC scan for incremental build"
                            return
                        } else {
                            scanpath = incrementalScanPath
                            echo "Scan Path for incremental = ${scanpath}"
                        }
                    }
                    // Build Python command
                    def cmd = """
                        python3 ${toolconfigPath}/CVC/run_dependency_check.py \
                        --projectname "${projectname}" \
                        --scanpath "${scanpath}" \
                        --outputfolder "${outputfolder}" \
                        --web_url "${web_url}" \
                        --webhook_token "${webhook_token}" \
                        --username "admin" \
                        --scantype "CVC" \
                        --buildId "${buildId}" \
                        --cvc_config_path "${cvcConfigPath}"
                    """.trim()
                    echo "Executing command:"
                    echo cmd
                    // Run command (foreground)
                    sh cmd
                    /*
        // OPTIONAL: Run in background
        sh """
            export JENKINS_NODE_COOKIE=dontKillMe
            nohup ${cmd} > cvc_scan.log 2>&1 &
            echo "CVC scan started in background."
        """
        */
                }
            }
        } //End CVC stage
        
 /*      stage("OSS") {
    steps {
        script {
            echo "Hello from OSS stage"

            def projconfigPath = params.PROJECT_CONFIG_PATH?.trim()
            def toolconfigPath = params.TOOLS_CONFIG_PATH?.trim()
            def orchconfigPath = params.ORCH_CONFIG_PATH?.trim()

            def ossConfigPath = params.OSS_CONFIG_PATH
            def webhook_token = params.WEBHOOK_TOKEN

            echo "Reading orchestration YAML from: '${projconfigPath}'"

            def yamlText = sh(
                script: "cat '${projconfigPath}'",
                returnStdout: true
            ).trim()

            def cfg = readYaml text: yamlText

            def projectname  = cfg.projectname
            def scanpath     = cfg.scanpath
            def outputfolder = cfg.outputfolder
            def web_url      = cfg.web_url
            def remotedir    = cfg.project_palamida_id

            def host = "palamida@172.18.228.174"
            def build_num = "${env.BUILD_NUM}"

            echo yamlText

            echo "projectname : ${projectname}"
            echo "scanpath    : ${scanpath}"
            echo "outputfolder: ${outputfolder}"
            echo "web_url     : ${web_url}"
            echo "remotedir   : ${remotedir}"

            def ocp = readYaml file: orchconfigPath
            def build_Type = ocp?.default_build_type as String

            echo "default_build_type = ${build_Type}"

            def FULL_CODEBASE = "/nfs_shared_files/${projectname}/test_7.0"
            def INCR_CODEBASE = "/nfs_shared_files/${projectname}/incremental/INCREMENTAL_CODEBASE"
            def GIT_DIFF_PATH = "/nfs_shared_files/${projectname}/incremental/changed_files.txt"

            echo "FULL_CODEBASE : ${FULL_CODEBASE}"
            echo "INCR_CODEBASE : ${INCR_CODEBASE}"
            echo "GIT_DIFF_PATH : ${GIT_DIFF_PATH}"

            // =====================================================
            // Run incremental copy logic on remote Palamida server
            // =====================================================
            sh """
                ssh -o BatchMode=yes \
                    -o StrictHostKeyChecking=no \
                    -o ConnectTimeout=20 \
                    ${host} 'bash -s' << 'REMOTE_SCRIPT'
                
                echo "Running incremental copy on remote server"
                hostname
                
                sudo mkdir -p "${INCR_CODEBASE}"
                
                echo "Reading paths from: ${GIT_DIFF_PATH}"
                
                if [ ! -f "${GIT_DIFF_PATH}" ]; then
                    echo "ERROR: Changed files file not found: ${GIT_DIFF_PATH}"
                    echo "Available files:"
                    ls -lah "/nfs_shared_files/${projectname}/incremental" || true
                    exit 1
                fi
                
                if [ ! -d "${FULL_CODEBASE}" ]; then
                    echo "ERROR: Full codebase directory not found: ${FULL_CODEBASE}"
                    exit 1
                fi
                
                while IFS= read -r relative_path; do
                    # Remove leading/trailing whitespaces
                    trimmed_path=\$(echo "\$relative_path" | xargs)
                
                    # Skip empty lines
                    [ -z "\$trimmed_path" ] && continue
                
                    echo "Processing: \$trimmed_path"
                
                    # Find full path inside FULL_CODEBASE
                    matched_files=\$(find "${FULL_CODEBASE}" -path "*\$trimmed_path*" -type f)
                
                    for file in \$matched_files; do
                        echo "Copying file: \$file"
                
                        # Construct destination directory in INCR_CODEBASE
                        rel_path=\$(realpath --relative-to="${FULL_CODEBASE}" "\$file")
                        dest_dir="${INCR_CODEBASE}/\$(dirname "\$rel_path")"
                
                        sudo mkdir -p "\$dest_dir"
                        sudo cp "\$file" "\$dest_dir/"
                    done
                done < "${GIT_DIFF_PATH}"
                
                echo "Incremental codebase creation completed."
                
                REMOTE_SCRIPT
            """

            // =====================================================
            // Run OSS scan on remote server
            // =====================================================
            def remote_cmd = """
                python3 run_oss_check_copy.py \
                --projectname "${projectname}" \
                 --version "test_7.0" \
                --scanpath "${scanpath}" \
                --outputfolder "${outputfolder}" \
                --web_url "${web_url}" \
                --webhook_token "${webhook_token}" \
                --scantype "OSS" \
                --oss_config_path "${ossConfigPath}" \
                --build_Type "${build_Type}" \
                --build_num "${build_num}" \
                --remote_dir "${remotedir}"
            """.trim()

           sh """
                ssh -o BatchMode=yes \
                    -o StrictHostKeyChecking=no \
                    -o ConnectTimeout=20 \
                    ${host} '${remote_cmd}'
            """
            
        }
    }
}//End of OSS stage  
*/

stage("SAST Scan") {

    agent {
        label 'nfs_sast_jile_node'
    }

    steps {
        script {
            echo "Hello from SAST stage"

            // -------------------------------------------------
            // Read and normalize input paths
            // -------------------------------------------------
            def projconfigPath = params.PROJECT_CONFIG_PATH?.trim()
            projconfigPath = projconfigPath.replaceFirst('^/shared_files', '')
            echo "projconfigPath = ${projconfigPath}"

            def orchconfigPath = params.ORCH_CONFIG_PATH?.trim()
            orchconfigPath = orchconfigPath.replaceFirst('^/shared_files', '')
            echo "orchconfigPath = ${orchconfigPath}"

            def toolconfigPath = params.TOOLS_CONFIG_PATH?.trim()
            toolconfigPath = toolconfigPath.replaceFirst('^/shared_files', '')
            echo "toolconfigPath = ${toolconfigPath}"

            def sastConfigPath = params.SAST_CONFIG_PATH?.trim()
            def webhook_token = params.WEBHOOK_TOKEN
            def buildId = "${env.BUILD_NUM}"
            def COMP_JAR = params.COMP_JAR

            echo "Reading orchestration YAML from: '${projconfigPath}'"

            // -------------------------------------------------
            // Read YAML file using PowerShell
            // -------------------------------------------------
            def yamlText = powershell(
                returnStdout: true,
                script: "Get-Content -Raw -LiteralPath '${projconfigPath}'"
            ).trim()

            def cfg = readYaml text: yamlText

            // -------------------------------------------------
            // Extract values from project YAML
            // -------------------------------------------------
            def projectname = cfg.projectname
            def version = cfg.scm_branch
            def scanpath = cfg.sourcepath
            def outputfolder = cfg.outputfolder
            def web_url = cfg.web_url
            def incr_scan_path = cfg.incr_scan_path

            echo "----- Orchestration YAML contents reading starts -----"
            echo yamlText

            echo "projectname      : ${projectname}"
            echo "version          : ${version}"
            echo "scanpath         : ${scanpath}"
            echo "outputfolder     : ${outputfolder}"
            echo "web_url          : ${web_url}"
            echo "SAST_CONFIG_PATH : ${sastConfigPath}"
            echo "WEBHOOK_TOKEN    : ${webhook_token}"
            echo "COMP_JAR         : ${COMP_JAR}"

            // -------------------------------------------------
            // Read orchestration config YAML
            // -------------------------------------------------
            def ocp = readYaml file: orchconfigPath
            def buildType = ocp?.default_build_type as String

            echo "default_build_type = ${buildType}"

            // -------------------------------------------------
            // Verify sourceanalyzer is available
            // -------------------------------------------------
            bat """
                echo Running source analyzer path check...
                where sourceanalyzer.exe
            """

            // -------------------------------------------------
            // Run SAST scan in background
            // -------------------------------------------------
            def basePathWin = "${cfg.shared_base}/${cfg.projectname}/${cfg.scm_branch}"
            basePathWin = basePathWin.replace("/shared_files", "R:")
            basePathWin = basePathWin.replace("/", "\\")
            
            def toolPathWin = toolconfigPath
            toolPathWin = toolPathWin.replace("/shared_files", "R:")
            toolPathWin = toolPathWin.replace("/", "\\")
            toolPathWin = toolPathWin.replaceAll('\\\\+$', '')

         /*   bat """
            
              powershell -NoProfile -Command "(Get-Content -Raw 'R:\\Orchestrator\\Tools\\SAST\\run_async_sast_scan.bat') -replace '&amp;gt;','>' -replace '&amp;lt;','<' -replace '&amp;amp;','&' -replace '&gt;','>' -replace '&lt;','<' -replace '&amp;','&' | Set-Content -Encoding ASCII 'R:\\Orchestrator\\Tools\\SAST\\run_async_sast_scan.bat'"
             
              findstr /n /c:"&gt;" /c:"&amp;" /c:"&lt;" "R:\\Orchestrator\\Tools\\SAST\\run_async_sast_scan.bat"
                
                call \"${toolPathWin}\\SAST\\run_async_sast_scan.bat\" ^
                \"${projectname}\" ^
                \"${version}\" ^
                \"${buildId}\" ^
                \"${sastConfigPath}\" ^
                \"${webhook_token}\" ^
                \"${buildType}\" ^
                \"${incr_scan_path}\" ^
                \"${web_url}\" ^
                \"${basePathWin}\\incr_source_code.txt\" ^
                \"${basePathWin}\\impacted_modules.txt\" ^
                \"${basePathWin}\\built_jar_paths.txt\" ^
                
            """  */
            
          /*	 bat """
                start "" /B cmd /c "call \"${toolconfigPath}\\SAST\\run_sast_check_incr.bat\" \"${projectname}\" \"${version}\" \"417\" \"${sastConfigPath}\" \"${webhook_token}\" \"${buildType}\" \"${incr_scan_path}\" \"${web_url}\" \"${COMP_JAR}\" >> \"${projectname}_${version}_sast.log\" 2>&1"
            """
			*/
			
		writeFile file: 'run_async_sast_scan_local.bat', text: '''@echo off
        setlocal EnableExtensions EnableDelayedExpansion
        
        echo =====================================================
        echo [INFO] Fortify Incremental Scan Started - LOCAL COPY
        echo =====================================================
        
        set "project=%~1"
        set "version=%~2"
        set "buildID=%~3"
        set "sastConfigFile=%~4"
        set "webhook_token=%~5"
        set "buildType=%~6"
        set "incr_scan_path=%~7"
        set "web_url=%~8"
        
        shift
        shift
        shift
        shift
        shift
        shift
        shift
        shift
        
        set "INCR_SRC_FILE=%~1"
        set "IMPACTED_MODULES_FILE=%~2"
        set "JAR_PATHS_FILE=%~3"
        
        echo [INFO] Project   : %project%
        echo [INFO] Version   : %version%
        echo [INFO] BuildID   : %buildID%
        echo [INFO] BuildType : %buildType%
        echo [DEBUG] INCR_SRC_FILE=%INCR_SRC_FILE%
        echo [DEBUG] IMPACTED_MODULES_FILE=%IMPACTED_MODULES_FILE%
        echo [DEBUG] JAR_PATHS_FILE=%JAR_PATHS_FILE%
        
        if not exist "%INCR_SRC_FILE%" (
            echo [ERROR] incr_source_code.txt not found: "%INCR_SRC_FILE%"
            exit /b 300
        )
        
        if not exist "%IMPACTED_MODULES_FILE%" (
            echo [ERROR] impacted_modules.txt not found: "%IMPACTED_MODULES_FILE%"
            exit /b 301
        )
        
        if not exist "%JAR_PATHS_FILE%" (
            echo [ERROR] built_jar_paths.txt not found: "%JAR_PATHS_FILE%"
            exit /b 302
        )
        
        for %%A in ("%INCR_SRC_FILE%") do set "INCR_SIZE=%%~zA"
        for %%A in ("%IMPACTED_MODULES_FILE%") do set "MODULE_SIZE=%%~zA"
        for %%A in ("%JAR_PATHS_FILE%") do set "JAR_SIZE=%%~zA"
        
        if "%INCR_SIZE%"=="0" (
            echo [INFO] No incremental changes detected. Skipping SAST Scan.
            exit /b 0
        )
        
        if "%MODULE_SIZE%"=="0" (
            echo [INFO] No impacted modules found. Skipping SAST Scan.
            exit /b 0
        )
        
        if "%JAR_SIZE%"=="0" (
            echo [INFO] No jar paths found. Skipping SAST Scan.
            exit /b 0
        )
        
        set "RUN_DIR=%CD%"
        
        if not exist "%RUN_DIR%\\output" (
            mkdir "%RUN_DIR%\\output"
        )
        
        set "TRANS_LOG=%RUN_DIR%\\output\\translate_%buildID%.log"
        set "SCAN_LOG=%RUN_DIR%\\output\\scan_%buildID%.log"
        
        echo [INFO] RUN_DIR=%RUN_DIR%
        echo [INFO] TRANS_LOG=%TRANS_LOG%
        echo [INFO] SCAN_LOG=%SCAN_LOG%
        
        set "sastConfigFile=%sastConfigFile:/shared_files=R:%"
        set "sastConfigFile=%sastConfigFile:/=\\%"
        
        echo [INFO] SAST Config File: %sastConfigFile%
        
        set "SA="
        
        for /f "delims=" %%S in ('where sourceanalyzer.exe 2^>nul') do (
            if not defined SA (
                set "SA=%%S"
            )
        )
        
        if not defined SA (
            echo [ERROR] sourceanalyzer.exe not found in PATH
            exit /b 211
        )
        
        echo [INFO] Using SourceAnalyzer: "%SA%"
        
        for /f "usebackq delims=" %%M in ("%IMPACTED_MODULES_FILE%") do (
            call :PROCESS_MODULE "%%M"
            if errorlevel 1 (
                exit /b 1
            )
        )
        
        echo -----------------------------------------------------
        echo [INFO] ALL MODULES COMPLETED
        echo -----------------------------------------------------
        exit /b 0
        
        
        :PROCESS_MODULE
        setlocal EnableDelayedExpansion
        
        set "MODULE=%~1"
        set "MODULE_WIN=%~1"
        
        set "MODULE_WIN=!MODULE_WIN:/u01/nfs_shared_files=R:!"
        set "MODULE_WIN=!MODULE_WIN:/shared_files=R:!"
        set "MODULE_WIN=!MODULE_WIN:/=\\!"
        
        set "MODULE_NAME="
        set "FILES_ARG="
        set "JAR_LIST="
        set "CP_ARG="
        
        for %%A in ("!MODULE_WIN!") do set "MODULE_NAME=%%~nxA"
        
        for %%B in ("%INCR_SRC_FILE%") do set "BASE_DIR=%%~dpB"
        if "!BASE_DIR:~-1!"=="\\" set "BASE_DIR=!BASE_DIR:~0,-1!"
        
        echo -----------------------------------------------------
        echo [INFO] Processing MODULE: !MODULE!
        echo [INFO] Module Windows Path: !MODULE_WIN!
        echo [INFO] Module Name: !MODULE_NAME!
        echo [INFO] BASE_DIR: !BASE_DIR!
        echo -----------------------------------------------------
        
        for /f "usebackq delims=" %%F in ("%INCR_SRC_FILE%") do (
            set "SRC_RAW=%%F"
            set "SRC_WIN=%%F"
        
            set "SRC_WIN=!SRC_WIN:/u01/nfs_shared_files=R:!"
            set "SRC_WIN=!SRC_WIN:/shared_files=R:!"
            set "SRC_WIN=!SRC_WIN:/=\\!"
        
            echo(!SRC_WIN! | findstr /L /I /C:"!MODULE_NAME!" >nul 2>nul
        
            if !errorlevel! EQU 0 (
                set "SRC_FILE=!SRC_WIN!"
        
                echo(!SRC_FILE! | findstr /R "^[A-Za-z]:" >nul 2>nul
                if errorlevel 1 (
                    set "SRC_FILE=!BASE_DIR!\\!SRC_WIN!"
                )
        
                if not exist "!SRC_FILE!" (
                    if exist "!BASE_DIR!\\dso_insights_plac\\!SRC_WIN!" (
                        set "SRC_FILE=!BASE_DIR!\\dso_insights_plac\\!SRC_WIN!"
                    )
                )
        
                if not exist "!SRC_FILE!" (
                    if exist "!BASE_DIR!\\!SRC_WIN!" (
                        set "SRC_FILE=!BASE_DIR!\\!SRC_WIN!"
                    )
                )
        
                if exist "!SRC_FILE!" (
                    echo [INFO] Valid source file: !SRC_FILE!
                    set "FILES_ARG=!FILES_ARG! ^"!SRC_FILE!^""
                ) else (
                    echo [WARN] Source file not found, skipping: !SRC_WIN!
                )
            )
        )
        
        if not defined FILES_ARG (
            echo [INFO] No valid changed source files found for module: !MODULE_NAME!
            echo [INFO] Skipping module: !MODULE_NAME!
            endlocal
            exit /b 0
        )
        
        echo [INFO] Final source files for Fortify:
        echo !FILES_ARG!
        
        for /f "usebackq delims=" %%J in ("%JAR_PATHS_FILE%") do (
            set "JAR_WIN=%%J"
        
            set "JAR_WIN=!JAR_WIN:/u01/nfs_shared_files=R:!"
            set "JAR_WIN=!JAR_WIN:/shared_files=R:!"
            set "JAR_WIN=!JAR_WIN:/=\\!"
        
            echo(!JAR_WIN! | findstr /L /I /C:"!MODULE_NAME!" >nul 2>nul
        
            if !errorlevel! EQU 0 (
                if exist "!JAR_WIN!" (
                    if defined JAR_LIST (
                        set "JAR_LIST=!JAR_LIST!;!JAR_WIN!"
                    ) else (
                        set "JAR_LIST=!JAR_WIN!"
                    )
                ) else (
                    echo [WARN] Jar path not found, skipping: !JAR_WIN!
                )
            )
        )
        
        if defined JAR_LIST (
            echo [INFO] Related jar/jars found:
            echo !JAR_LIST!
            set "CP_ARG=-cp ^"!JAR_LIST!^""
        ) else (
            echo [WARN] No valid related jar found for module: !MODULE_NAME!
            echo [WARN] Continuing translate without classpath.
            set "CP_ARG="
        )
        
        set "BASE_NAME=!MODULE_NAME!_%buildID%"
        set "FPR_FILE=%project%_!MODULE_NAME!_%version%_%buildID%.fpr"
        set "FPR_OUT=%RUN_DIR%\\output\\!FPR_FILE!"
        
        echo [INFO] BASE_NAME=!BASE_NAME!
        echo [INFO] FPR_OUT=!FPR_OUT!
        
        echo [INFO] TRANSLATE START
        echo [DEBUG] Final Fortify translate command:
        echo "!SA!" -b "!BASE_NAME!" -source 11 !CP_ARG! !FILES_ARG! -logfile "!TRANS_LOG!"
        
        "%SA%" -b "!BASE_NAME!" -source 11 !CP_ARG! !FILES_ARG! -logfile "!TRANS_LOG!"
        :: "%SA%" -b "!BASE_NAME!" -Dcom.fortify.sca.Preserve=true !CP_ARG! !FILES_ARG! -logfile "!TRANS_LOG!"
        
        if errorlevel 1 (
            echo [ERROR] TRANSLATE failed for module: !MODULE_NAME!
            echo [ERROR] Check log: "!TRANS_LOG!"
            endlocal
            exit /b 401
        )
        
        echo [INFO] SCAN START
        
        "%SA%" -Xmx20480m -b "!BASE_NAME!" -scan -f "!FPR_OUT!" -logfile "!SCAN_LOG!"
        
        if errorlevel 1 (
            echo [ERROR] SCAN failed for module: !MODULE_NAME!
            echo [ERROR] Check log: "!SCAN_LOG!"
            endlocal
            exit /b 402
        )
        
        if exist "!FPR_OUT!" (
            echo [SUCCESS] FPR created: !FPR_OUT!
        ) else (
            echo [ERROR] FPR was not created for module: !MODULE_NAME!
            endlocal
            exit /b 403
        )
        
        REM =====================================================
        REM WEBHOOK FOR CURRENT COMPONENT
        REM =====================================================
        
        set "SCAN_TYPE=SAST"
        set "STATUS=SUCCESS"
        set "RESULT_FPR=!FPR_OUT!"
        set "COMPONENT=!MODULE_NAME!"
        set "BASE_NAME_VAR=!BASE_NAME!"
        set "BUILD_TYPE=%buildType%"
        set "BUILD_ID=%buildID%"
        set "PAYLOAD_FILE=%RUN_DIR%\\output\\webhook_payload_!BASE_NAME!.json"
        set "WEBHOOK_URL=%web_url%"
        
        if defined webhook_token (
            echo(!WEBHOOK_URL! | findstr /I /C:"token=" >nul 2>nul
            if errorlevel 1 (
                echo(!WEBHOOK_URL! | findstr /C:"?" >nul 2>nul
                if errorlevel 1 (
                    set "WEBHOOK_URL=!WEBHOOK_URL!?token=%webhook_token%"
                ) else (
                    set "WEBHOOK_URL=!WEBHOOK_URL!^&token=%webhook_token%"
                )
            ) else (
                if "!WEBHOOK_URL:~-6!"=="token=" (
                    set "WEBHOOK_URL=!WEBHOOK_URL!%webhook_token%"
                )
            )
        )
        
        echo [INFO] Triggering webhook for component: !COMPONENT!
        echo [INFO] Final Webhook URL: !WEBHOOK_URL!
        
        call :SEND_WEBHOOK
        
        if errorlevel 1 (
            echo [ERROR] Webhook failed for module: !MODULE_NAME!
            endlocal
            exit /b 404
        )
        
        echo [SUCCESS] Webhook completed for module: !MODULE_NAME!
        
        endlocal
        exit /b 0
        
        
        :: =========================
        :: WEBHOOK PER COMPONENT
        :: =========================
        :SEND_WEBHOOK
        setlocal EnableDelayedExpansion
        set "USERNAME=admin"

        
        powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -Command "$payload = [ordered]@{ SCAN_TYPE=$env:SCAN_TYPE; USERNAME='%USERNAME%' ; status=$env:STATUS; FPR_FILE=$env:RESULT_FPR; COMPONENT=$env:COMPONENT; BASENAME=$env:BASE_NAME_VAR; buildType=$env:BUILD_TYPE; buildID=$env:BUILD_ID; project=$env:project }; $json = $payload | ConvertTo-Json -Compress; [System.IO.File]::WriteAllText($env:PAYLOAD_FILE, $json, (New-Object System.Text.UTF8Encoding($false)))"
        
        if errorlevel 1 (
            echo [ERROR] PowerShell JSON payload creation failed.
            endlocal & exit /b 302
        )
        
        if not exist "!PAYLOAD_FILE!" (
            echo [ERROR] Payload file was not created: !PAYLOAD_FILE!
            endlocal & exit /b 302
        )
        
        for %%P in ("!PAYLOAD_FILE!") do set "PAYLOAD_SIZE=%%~zP"
        
        if "!PAYLOAD_SIZE!"=="0" (
            echo [ERROR] Payload file is empty: !PAYLOAD_FILE!
            endlocal & exit /b 302
        )
        
        echo [INFO] Webhook payload:
        type "!PAYLOAD_FILE!"
        
        echo.
        echo [INFO] Webhook URL: !WEBHOOK_URL!
        
        for /f "tokens=*" %%H in ('
        curl -s -o nul -w "%%{http_code}" -X POST ^
        -H "Content-Type: application/json" ^
        --data-binary "@!PAYLOAD_FILE!" ^
        "!WEBHOOK_URL!"
        ') do (
            set "HTTP_STATUS=%%H"
        )
        
        echo [INFO] Webhook HTTP status: !HTTP_STATUS!
        
        set "STATUS_FIRST_DIGIT=!HTTP_STATUS:~0,1!"
        
        if /I "!STATUS_FIRST_DIGIT!"=="2" (
            echo [OK] Webhook call completed successfully.
            endlocal & exit /b 0
        )
        
        echo [ERROR] Webhook call failed HTTP !HTTP_STATUS!.
        endlocal & exit /b 301
        '''
            
            bat """
            call "%WORKSPACE%\\run_async_sast_scan_local.bat" ^
            "Insights_PLAC" ^
            "test_7.0" ^
            "${buildId}" ^
            "/shared_files/Orchestrator/Tools/SAST/fortify_sast.yaml" ^
            "dso_generic_receiver_token" ^
            "incremental" ^
            "R:\\Insights_PLAC\\incremental" ^
            "http://172.18.228.57:8081/generic-webhook-trigger/invoke?token=" ^
            "R:\\Insights_PLAC\\test_7.0\\incr_source_code.txt" ^
            "R:\\Insights_PLAC\\test_7.0\\impacted_modules.txt" ^
            "R:\\Insights_PLAC\\test_7.0\\built_jar_paths.txt"
            """
        }
    }
}
   
    }
} //End NFR activity



    } //Stages ends

    post {
        failure {
            sh 'echo "Build Failed"'
            updateGitlabCommitStatus name: "Jenkins Build #$BUILD_NUM", state: 'failed'
        }

        success {
            sh 'echo "Build success"'
            updateGitlabCommitStatus name: "Jenkins Build #$BUILD_NUM", state: 'success'
        }
    }
}
