#!groovy
def BN = BRANCH_NAME == "master" || BRANCH_NAME.startsWith("releases/") ? BRANCH_NAME : "master"

//library "knime-pipeline@$BN"
library "knime-pipeline@integratedWorkflowtests"

properties([
	pipelineTriggers([upstream(
		'knime-shared/' + env.BRANCH_NAME.replaceAll('/', '%2F')
	)]),
	parameters(workflowTests.getConfigurationsAsParameters()),
	buildDiscarder(logRotator(numToKeepStr: '5')),
	disableConcurrentBuilds()
])

try {
    buildConfigs = [
        'Tycho Build': {
	        knimetools.defaultTychoBuild('org.knime.update.core')
        },
    ]

    for (c in workflowTests.getActiveConfigurations()) {
        buildConfigs['Testing: ' + c] = {
            node("${c} && workflow-tests") {
                checkout scm
                knimetools.runIntegratedWorkflowTests(profile: 'test')
            }
        }
    }

    parallel buildConfigs

    workflowTests.runTests(
        dependencies: [
            repositories: ['knime-json', 'knime-python', 'knime-filehandling',
                'knime-datageneration', 'knime-jep', 'knime-js-base', 'knime-cloud', 'knime-database', 'knime-kerberos',
				'knime-textprocessing', 'knime-dl4j', 'knime-virtual', 'knime-r', 'knime-streaming', 'knime-cluster']
        ]
    )

	stage('Sonarqube analysis') {
		env.lastStage = env.STAGE_NAME
		workflowTests.runSonar()
	}
} catch (ex) {
	currentBuild.result = 'FAILURE'
	throw ex
} finally {
	notifications.notifyBuild(currentBuild.result);
}
/* vim: set shiftwidth=4 expandtab smarttab: */
