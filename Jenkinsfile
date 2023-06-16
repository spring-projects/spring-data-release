def p = [:]
node {
	checkout scm
	p = readProperties interpolate: true, file: 'ci/release.properties'
}

pipeline {
	agent none

	triggers {
        pollSCM 'H/10 * * * *'
    }

	options {
		disableConcurrentBuilds()
		buildDiscarder(logRotator(numToKeepStr: '14'))
	}

	stages {

		stage('Build the Spring Data release tools container') {
			when {
				anyOf {
				   changeset 'ci/Dockerfile'
				   changeset 'ci/java-tools.properties'
				}
			}
			agent {
				label 'data'
			}

			steps {
				script {
					def image = docker.build("springci/spring-data-release-tools:0.9", "ci")
					docker.withRegistry('', 'hub.docker.com-springbuildmaster') {
						image.push()
					}
				}
			}
		}

		stage('Ship It') {
			when {
				branch 'release'
			}
			agent {
				docker {
					image 'springci/spring-data-release-tools:0.9'
				}
			}
			options { timeout(time: 4, unit: 'HOURS') }

			environment {
				GITHUB = credentials('3a20bcaa-d8ad-48e3-901d-9fbc941376ee')
				GITHUB_TOKEN = credentials('7b3ebbea-7001-479b-8578-b8c464dab973')
				REPO_SPRING_IO = credentials('repo_spring_io-jenkins-release-token')
				ARTIFACTORY = credentials('02bd1690-b54f-4c9f-819d-a77cb7a9822c')
				STAGING_PROFILE_ID = credentials('spring-data-release-deployment-maven-central-staging-profile-id')
				MAVEN_SIGNING_KEY = credentials('spring-gpg-private-key')
				MAVEN_SIGNING_KEY_PASSWORD = credentials('spring-gpg-passphrase')
				GIT_SIGNING_KEY = credentials('spring-gpg-github-private-key-jenkins')
				GIT_SIGNING_KEY_PASSWORD = credentials('spring-gpg-github-passphrase-jenkins')
				SONATYPE = credentials('oss-login')
			}

			steps {
				script {
					sh "ci/build-spring-data-release-cli.bash"

					sh "ci/build-and-distribute.bash ${p['release.version']}"

					slackSend(
						color: (currentBuild.currentResult == 'SUCCESS') ? 'good' : 'danger',
						channel: '#spring-data-dev',
						message: (currentBuild.currentResult == 'SUCCESS')
								? "`${env.BUILD_URL}` - Build and distribute ${p['release.version']} passed! Release the build (if needed)."
								: "`${env.BUILD_URL}` - Build and distribute ${p['release.version']} failed!")
				}
			}
		}
	}

	post {
		changed {
			script {
				slackSend(
						color: (currentBuild.currentResult == 'SUCCESS') ? 'good' : 'danger',
						channel: '#spring-data-dev',
						message: "${currentBuild.fullDisplayName} - `${currentBuild.currentResult}`\n${env.BUILD_URL}")
				emailext(
						subject: "[${currentBuild.fullDisplayName}] ${currentBuild.currentResult}",
						mimeType: 'text/html',
						recipientProviders: [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']],
						body: "<a href=\"${env.BUILD_URL}\">${currentBuild.fullDisplayName} is reported as ${currentBuild.currentResult}</a>")
			}
		}
	}
}
