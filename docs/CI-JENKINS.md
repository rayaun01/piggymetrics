# Jenkins equivalent

This is a documented equivalent of the GitHub Actions pipeline. It is not
implemented as active CI in this repository.

```groovy
pipeline {
    agent any

    tools {
        jdk 'Temurin 8'
    }

    stages {
        stage('checkout') {
            steps {
                checkout scm
            }
        }

        stage('JDK 8 tool') {
            steps {
                // The JDK tool installer supplies JAVA_HOME on the agent.
                sh 'java -version'
            }
        }

        stage('verify') {
            steps {
                sh 'mvn -B -fae verify'
            }
        }

        stage('verify full profile') {
            steps {
                sh 'mvn -B -fae -Pfull verify'
            }
        }

        stage('JUnit reports') {
            steps {
                junit testResults: '**/target/surefire-reports/*.xml',
                      allowEmptyResults: true
            }
        }

        stage('JaCoCo') {
            steps {
                jacoco execPattern: '**/target/jacoco.exec',
                      classPattern: '**/target/classes',
                      sourcePattern: '**/src/main/java'
            }
        }

        stage('archive artifacts') {
            steps {
                archiveArtifacts artifacts: '**/target/*.jar,**/target/site/jacoco/**',
                                 allowEmptyArchive: true
            }
        }
    }
}
```

| GitHub Actions concept | Jenkins equivalent |
| --- | --- |
| `setup-java` | Jenkins Tool/JDK configuration or `withEnv JAVA_HOME` |
| Actions cache | `stash` or the Pipeline Maven plugin local-repository cache |
| Artifact upload | `archiveArtifacts` |
| `workflow_dispatch` | Parameters and a manual Build |
| Full-profile verification | A separate `verify full profile` stage |

An internal Jenkins installation additionally needs a Maven mirror and
`settings.xml`: internal networks commonly cannot reach Maven Central
directly. This environment encountered HTTP 429 responses from Maven Central,
so its local verification used an external mirror settings file.
