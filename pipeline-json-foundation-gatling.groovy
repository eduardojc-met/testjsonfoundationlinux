pipeline {
 
  environment {
       IBM_ACCESS_KEY_ID     = credentials('ibmuser')
        IBM_SECRET_ACCESS_KEY = credentials('ibmkey')
        //project=credentials('project-name')
        gatlingConf=""
        url_for_gatling=''

    }
 
  agent any
 
  stages {
 


  stage('Checkout Source') {
      
      steps {
        git "https://github.com/eduardojc-met/testjsonfoundationlinux.git"
      }
    }
 

    stage('Foundation Steps') {
      
      steps {
        script{


def json = readJSON file: 'configurations.json' 

echo json[0].stepsFile.toString()

def foundationConf=json[0].stepsFile.toString()
gatlingConf=json[1].stepsFile.toString()

def pipelineFoundation = load foundationConf
sh 'mkdir -p foundation'
dir("foundation") {
pipelineFoundation.start("","","","","","","","")
}

 dir("foundation/src/main/docker/frontend") {

   	def ingress = readYaml file:"ingress.yaml"
    url_for_gatling ="https://"+ingress.spec.rules[0]["host"]
 }



  }
   


    }
      }




 stage('Gatling steps') {
   steps{
     script{


   def gatling_pipeline = load "pipeline-gatling.groovy"
           
sh 'mkdir -p gatling'
def pipelineGatling = load gatlingConf
dir("gatling") {
pipelineGatling.start("${url_for_gatling}")
}


     }
   
   }

 }

   //EN esta patde se depliega el pod nginx con los resultados

     stage('Display Gatling results') {
   steps{

     script{

def folderName=""

dir("gatling/target/gatling"){

folderName= sh(script: "cat lastRun.txt", returnStdout: true)
folderName=folderName.split(" ")[2].replace(" ","").replace("\n","").trim()//TODO

}


dir("gatling/"){
def dockerf= readFile "Dockerfile"

dockerf=dockerf.replace("folder","${folderName}")

sh "rm Dockerfile"
writeFile file: 'Dockerfile', text: dockerf


 sh 'docker build -t gatlingresults-nginx . && docker tag gatlingresults-nginx de.icr.io/devops-tools/gatlingresults-nginx'


}

            
             sh label: 'Login to ibmcloud', script: '''ibmcloud.exe login -u %IBM_ACCESS_KEY_ID% -p %IBM_SECRET_ACCESS_KEY% -r eu-de ''' 
           sh label: 'Login to ibm cr', script: '''ibmcloud.exe  cr login '''
           sh label: 'Configuring kubernetes', script: '''ibmcloud.exe ks cluster config -c c7pb9mkf09cf7vh8tmu0 '''
dir("gatling"){
            sh "docker push de.icr.io/devops-tools/gatlingresults-nginx"
            sh 'kubectl apply -f deployment.yaml --namespace=develop'
            sh 'kubectl apply -f service.yaml --namespace=develop'
            sh 'kubectl apply -f ingress.yaml --namespace=develop'

}  
                
      


     }


     }
   


 }
 


        }
          }
