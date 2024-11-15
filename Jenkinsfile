def p = [:]
node {
	checkout scm
	p = readProperties interpolate: true, file: 'ci/release.properties'
}

pipeline {
	agent none

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
				label 'e2-standard-4'
			}

			steps {
				script {
					def image = docker.build("springci/spring-data-release-tools:0.20", "ci")
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
				label 'e2-standard-4'
				docker {
					image 'springci/spring-data-release-tools:0.20'
				}
			}

			options { timeout(time: 4, unit: 'HOURS') }

			environment {
				GITHUB = credentials('3a20bcaa-d8ad-48e3-901d-9fbc941376ee')
				GITHUB_TOKEN = credentials('7b3ebbea-7001-479b-8578-b8c464dab973')
				REPO_SPRING_IO = credentials('repo_spring_io-jenkins-release-token')
				ARTIFACTORY = credentials('02bd1690-b54f-4c9f-819d-a77cb7a9822c')
				COMMERCIAL = credentials('usw1_packages_broadcom_com-jenkins-token')
				STAGING_PROFILE_ID = credentials('spring-data-release-deployment-maven-central-staging-profile-id')
				MAVEN_SIGNING_KEY = credentials('spring-gpg-private-key')
				MAVEN_SIGNING_KEY_PASSWORD = credentials('spring-gpg-passphrase')
				GIT_SIGNING_KEY = credentials('spring-gpg-github-private-key-jenkins')
				GIT_SIGNING_KEY_PASSWORD = credentials('spring-gpg-github-passphrase-jenkins')
				SONATYPE = credentials('oss-s01-token')
			}

			steps {
				script {
					sh "ci/build-spring-data-release-cli.bash"
					sh "ci/build-and-distribute.bash ${p['release.version']}"
				}
			}
		}
	}

	post {
		changed {
			script {
				emailext(
						subject: "[${currentBuild.fullDisplayName}] ${currentBuild.currentResult}",
						mimeType: 'text/html',
						recipientProviders: [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']],
						body: "<a href=\"${env.BUILD_URL}\">${currentBuild.fullDisplayName} is reported as ${currentBuild.currentResult}</a>")
			}
		}
	}
}
