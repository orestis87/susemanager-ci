def run(params) {
    timestamps {
        deployed = false
        env.resultdir = "${WORKSPACE}/results"
        env.resultdirbuild = "${resultdir}/${BUILD_NUMBER}"
        // The junit plugin doesn't affect full paths
        junit_resultdir = "results/${BUILD_NUMBER}/results_junit"
        env.common_params = "--outputdir ${resultdir} --tf ${params.tf_file} --gitfolder ${resultdir}/sumaform"
        try {
            stage('Clone terracumber, susemanager-ci and sumaform') {
                // Create a directory for  to place the directory with the build results (if it does not exist)
                sh "mkdir -p ${resultdir}"
                git url: params.terracumber_gitrepo, branch: params.terracumber_ref
                dir("susemanager-ci") {
                    checkout scm
                }
                // Clone sumaform
                sh "set +x; source /home/jenkins/.credentials set -x; ./terracumber-cli ${common_params} --gitrepo ${params.sumaform_gitrepo} --gitref ${params.sumaform_ref} --runstep gitsync"
            
                // Restore Terraform states from artifacts
                if (params.use_previous_terraform_state) {
                    copyArtifacts projectName: currentBuild.projectName, selector: specific("${currentBuild.previousBuild.number}")
                }
            }
            stage('Deploy') {
                if(params.must_deploy) {
                    // Provision the environment
                    if (params.terraform_init) {
                        env.TERRAFORM_INIT = '--init'
                    } else {
                        env.TERRAFORM_INIT = ''
                    }
                    // Generate json file in the workspace
                    writeFile file: 'custom_repositories.json', text: params.custom_repositories, encoding: "UTF-8"
                    // Run Terracumber to deploy the environment
                    sh "set +x; source /home/jenkins/.credentials set -x; export TF_VAR_CUCUMBER_GITREPO=${params.cucumber_gitrepo}; export TF_VAR_CUCUMBER_BRANCH=${params.cucumber_ref}; export TERRAFORM=${params.terraform_bin}; export TERRAFORM_PLUGINS=${params.terraform_bin_plugins}; ./terracumber-cli ${common_params} --logfile ${resultdirbuild}/sumaform.log ${env.TERRAFORM_INIT} --taint '.*(domain|main_disk|data_disk|server_extra_nfs_mounts).*' --custom-repositories ${WORKSPACE}/custom_repositories.json --runstep provision"
                    // Generate features
                    sh "./terracumber-cli ${common_params} --logfile ${resultdirbuild}/testsuite.log --runstep cucumber --cucumber-cmd 'export BUILD_VALIDATION=true; cd /root/spacewalk/testsuite; rake utils:generate_build_validation_features'"
                    deployed = true
                }
            }
            stage('Run Core features') {
                if(params.must_run_core && (deployed || !params.must_deploy)) {
                    sh "./terracumber-cli ${common_params} --logfile ${resultdirbuild}/testsuite.log --runstep cucumber --cucumber-cmd 'export BUILD_VALIDATION=true; cd /root/spacewalk/testsuite; rake cucumber:build_validation_core'"
                }
            }
            stage('Sync. products and channels') {
                if(params.must_sync && (deployed || !params.must_deploy)) {
                    sh "./terracumber-cli ${common_params} --logfile ${resultdirbuild}/testsuite.log --runstep cucumber --cucumber-cmd 'export BUILD_VALIDATION=true; cd /root/spacewalk/testsuite; rake cucumber:build_validation_reposync'"
                    sh "./terracumber-cli ${common_params} --logfile ${resultdirbuild}/testsuite.log --runstep cucumber --cucumber-cmd 'export BUILD_VALIDATION=true; cd /root/spacewalk/testsuite; rake cucumber:build_validation_wait_for_product_reposync'"
                }
            }

            stage('Add MUs') {
                if(params.must_add_channels) {
                    echo 'Add custom channels and MU repositories'
                    res_mu_repos = sh(script: "./terracumber-cli ${common_params} --logfile ${resultdirbuild}/testsuite.log --runstep cucumber --cucumber-cmd 'export BUILD_VALIDATION=true; cd /root/spacewalk/testsuite; rake ${params.rake_namespace}:build_validation_add_custom_repositories'", returnStatus: true)
                    sh "./terracumber-cli ${common_params} --logfile ${resultdirbuild}/testsuite.log --runstep cucumber --cucumber-cmd 'export BUILD_VALIDATION=true; cd /root/spacewalk/testsuite; rake cucumber:build_validation_wait_for_custom_reposync'"
                    echo "Custom channels and MU repositories status code: ${res_mu_repos}"
                }
            }

            stage('Add Activation Keys') {
                if(params.must_add_keys) {
                    echo 'Add Activation Keys'
                    res_add_keys = sh(script: "./terracumber-cli ${common_params} --logfile ${resultdirbuild}/testsuite.log --runstep cucumber --cucumber-cmd 'export BUILD_VALIDATION=true; cd /root/spacewalk/testsuite; rake ${params.rake_namespace}:build_validation_add_activation_keys'", returnStatus: true)
                    echo "Add Activation Keys status code: ${res_add_keys}"
                }
            }

            stage('Create bootstrap repositories') {
                if(params.must_create_bootstrap_repos) {
                    echo 'Create bootstrap repositories'
                    res_create_bootstrap_repos = sh(script: "./terracumber-cli ${common_params} --logfile ${resultdirbuild}/testsuite.log --runstep cucumber --cucumber-cmd 'export BUILD_VALIDATION=true; cd /root/spacewalk/testsuite; rake cucumber:build_validation_create_bootstrap_repositories'", returnStatus: true)
                    echo "Create bootstrap repositories code: ${res_create_bootstrap_repos}"
                }
            }

            stage('Bootstrap Proxy') {
                if(params.must_boot_proxy) {
                    echo 'Proxy register as minion with gui'
                    res_init_proxy = sh(script: "./terracumber-cli ${common_params} --logfile ${resultdirbuild}/testsuite.log --runstep cucumber --cucumber-cmd 'export BUILD_VALIDATION=true; cd /root/spacewalk/testsuite; rake ${params.rake_namespace}:build_validation_init_proxy'", returnStatus: true)
                    echo "Init Proxy status code: ${res_init_proxy}"
                }
            }

            stage('Bootstrap clients') {
                if(params.must_boot_clients) {
                    echo 'Bootstrap clients'
                    res_init_clients = sh(script: "./terracumber-cli ${common_params} --logfile ${resultdirbuild}/testsuite.log --runstep cucumber --cucumber-cmd 'export CAPYBARA_TIMEOUT=${params.capybara_timeout}; export DEFAULT_TIMEOUT=${params.default_timeout}; export BUILD_VALIDATION=true; cd /root/spacewalk/testsuite; rake ${params.rake_namespace}:build_validation_init_clients'", returnStatus: true)
                    echo "Init clients status code: ${res_init_clients}"
                }
            }

            stage('Run Smoke Tests') {
                if(params.must_run_tests) {
                        echo 'Run Smoke tests'
                        res_smoke_tests = sh(script: "./terracumber-cli ${common_params} --logfile ${resultdirbuild}/testsuite.log --runstep cucumber --cucumber-cmd 'export CAPYBARA_TIMEOUT=${params.capybara_timeout}; export DEFAULT_TIMEOUT=${params.default_timeout}; export BUILD_VALIDATION=true; cd /root/spacewalk/testsuite; rake ${params.rake_namespace}:build_validation_smoke_tests'", returnStatus: true)
                        echo "Smoke tests status code: ${res_smoke_tests}"
                }
            }
        }
        finally {
            stage('Save TF state') {
                    archiveArtifacts artifacts: "results/sumaform/terraform.tfstate, results/sumaform/.terraform/**/*"
            }
            stage('Get results') {
                def error = 0
                if (deployed || !params.must_deploy) {
                    try {
                        sh "./terracumber-cli ${common_params} --logfile ${resultdirbuild}/testsuite.log --runstep cucumber --cucumber-cmd 'export BUILD_VALIDATION=true; cd /root/spacewalk/testsuite; rake cucumber:build_validation_finishing'"
                    } catch(Exception ex) {
                        println("ERROR: rake cucumber:build_validation_finishing failed")
                        error = 1
                    }
                    try {
                        sh "./terracumber-cli ${common_params} --logfile ${resultdirbuild}/testsuite.log --runstep cucumber --cucumber-cmd 'export BUILD_VALIDATION=true; cd /root/spacewalk/testsuite; rake utils:generate_test_report'"
                    } catch(Exception ex) {
                        println("ERROR: rake utils:generate_test_report failed")
                        error = 1
                    }
                    sh "./terracumber-cli ${common_params} --logfile ${resultdirbuild}/testsuite.log --runstep getresults"
                    publishHTML( target: [
                                allowMissing: true,
                                alwaysLinkToLastBuild: false,
                                keepAll: true,
                                reportDir: "${resultdirbuild}/cucumber_report/",
                                reportFiles: 'cucumber_report.html',
                                reportName: "Build Validation report"]
                    )
                    junit allowEmptyResults: true, testResults: "${junit_resultdir}/*.xml"
                }
                // Send email
                sh "./terracumber-cli ${common_params} --logfile ${resultdirbuild}/mail.log --runstep mail"
                // Clean up old results
                sh "./clean-old-results -r ${resultdir}"
                sh "exit ${error}"
            }
        }
    }
}

return this
