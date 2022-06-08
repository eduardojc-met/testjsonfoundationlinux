
def start(String IBM_ACCESS_KEY_ID,String IBM_SECRET_ACCESS_KEY, String git_commit,String front_image_push_id,String back_image_push_id,String url_for_gatling,String timestamp_front, String  timestamp_back){

 
 

 

 
    stage('Checkout Source') {
      
      
           script{
      def git_command=  git 'https://github.com/eduardojc-met/testgatling.git'
      
           git_commit= git_command["GIT_COMMIT"]
           
          
      }
    
      
    }
 
 
   stage('build image') {
       
      
        script{
            
        def packageJSON = readJSON file: 'package.json'
        def packageJSONVersion = packageJSON.version
        def appname=readMavenPom().getArtifactId()
        def now = new Date()
        timestamp_front = now.format("yyMMdd.hhmmss", TimeZone.getTimeZone("GMT+8"))
           sh 'docker build -t edujc/frontnginxjunto:'+"${packageJSONVersion}"+' -f src/main/docker/frontend/Dockerfile . && docker tag edujc/frontnginxjunto:'+"${packageJSONVersion}"+' de.icr.io/devops-tools/'+"${appname}"+'-fe:'+"${packageJSONVersion}"+"_"+"${timestamp_front}"
           sh 'mvn clean package -DskipTests -Pprod,tls '
           timestamp_back = now.format("yyMMdd.hhmmss", TimeZone.getTimeZone("GMT+8"))
           sh 'docker build -t edujc/backjunto:'+readMavenPom().getVersion()+' -f src/main/docker/backend/Dockerfile . && docker tag edujc/backjunto:'+readMavenPom().getVersion()+' de.icr.io/devops-tools/'+"${appname}"+'-bff:'+readMavenPom().getVersion()+"_"+"${timestamp_back}"
        
        
        
        
        
        
        }
 
        
        
     
      }
    
 
    stage ('push images') {
        
        script{
             def packageJSON = readJSON file: 'package.json'
        def packageJSONVersion = packageJSON.version 
        def pomVersion = readMavenPom().getVersion()
        def appname=readMavenPom().getArtifactId()
           
                
               
            sh label: 'Login to ibmcloud', script: '''ibmcloud.exe login -u %IBM_ACCESS_KEY_ID% -p %IBM_SECRET_ACCESS_KEY% -r eu-de ''' 
            sh label: 'Install ibmcloud ks plugin', script: '''echo n | ibmcloud.exe plugin install container-service ''' 
            sh label: 'Install ibmcloud cr plugin', script: '''echo n | ibmcloud.exe  plugin install container-registry ''' 
            sh label: 'Login to ibm cr', script: '''ibmcloud.exe  cr login '''
            sh label: 'Configuring kubernetes', script: '''ibmcloud.exe ks cluster config -c c7pb9mkf09cf7vh8tmu0
 '''

            sh 'docker push de.icr.io/devops-tools/'+"${appname}"+'-fe:'+"${packageJSONVersion}"+"_"+"${timestamp_front}"
            sh 'docker push de.icr.io/devops-tools/'+"${appname}"+'-bff:'+"${pomVersion}"+"_"+"${timestamp_back}"
            
            def full_id_front = sh(script:"docker inspect --format={{.RepoDigests}} de.icr.io/devops-tools/"+"${appname}"+'-fe:'+"${packageJSONVersion}"+"_"+"${timestamp_front}", returnStdout: true) //TODO
def id_arr_front=full_id_front.toString().split('sha256:')
front_image_push_id = id_arr_front[1].toString().replace("de.icr.io/devops-tools/"+"${appname}"+'-fe',"").replace("'","").replace("\n","").replace("]","")

def full_id_back = sh(script:"docker inspect --format={{.RepoDigests}} de.icr.io/devops-tools/"+"${appname}"+'-bff:'+"${pomVersion}"+"_"+"${timestamp_back}", returnStdout: true) 
def id_arr_back=full_id_back.toString().split('sha256:')
back_image_push_id = id_arr_back[1].toString().replace("de.icr.io/devops-tools/"+"${appname}"+'-bff:',"").replace("'","").replace("\n","").replace("]","")
           
    
            
                    
         
        }
        
  
    } 
 


    stage('Deploying App to Kubernetes') {
      
        script {

	
            def appname=readMavenPom().getArtifactId()
            
            def packageJSON = readJSON file: 'package.json'
        def packageJSONVersion = packageJSON.version 
        def pomVersion = readMavenPom().getVersion()
             dir("src/main/docker/backend") {
            
              def datas = readYaml file:"back.yaml"
               datas[0].metadata.labels=['io.kompose.service': "${appname}"+'-bff']
               
               
                 datas[0].metadata.annotations["last-image-push-id"]=back_image_push_id
          
       
                datas[0].metadata.annotations["last-commit-sha"]=git_commit

               
               
               datas[0].metadata["name"]="${appname}"+'-bff'
               datas[0].spec.selector.matchLabels=['io.kompose.service': "${appname}"+'-bff']
               datas[0].spec.template.spec.containers[0]["name"]="${appname}"+'-bff'
               datas[0].spec.template.spec.containers[0]["image"]='de.icr.io/devops-tools/'+"${appname}"+'-bff:'+"${pomVersion}"+"_"+"${timestamp_back}"

               
                datas[0].spec.template.metadata.labels=['io.kompose.service': "${appname}"+'-bff']
              
              
              datas[1].metadata.labels=['io.kompose.service': "${appname}"+'-bff']
              datas[1].metadata["name"]="${appname}"+'-bff'
               datas[1].spec.selector=['io.kompose.service': "${appname}"+'-bff']
              
                sh 'rm back.yaml'
                writeYaml file: 'back.yaml', data: datas[0]
                sh 'rm serviceback.yaml > /dev/null'
                writeYaml file: 'serviceback.yaml', data: datas[1]
               
         
             }
            
            dir("src/main/docker/frontend") {
            
              def datas = readYaml file:"front.yaml"
               datas[0].metadata.labels=['io.kompose.service': "${appname}"+'-fe']
               
               
               
               datas[0].metadata["name"]="${appname}"+'-fe'
               datas[0].spec.selector.matchLabels=['io.kompose.service': "${appname}"+'-fe']
              
                 
                 
                 datas[0].metadata.annotations["last-image-push-id"]=front_image_push_id
          
       
                datas[0].metadata.annotations["last-commit-sha"]=git_commit

               datas[0].spec.template.metadata.labels=['io.kompose.service': "${appname}"+'-fe']
               datas[0].spec.template.spec.containers[0]["name"]="${appname}"+'-fe'
               // datas[0].spec.template.spec.containers[0]["image"]='de.icr.io/devops-tools/'+"${appname}"+'-fe:'+"${packageJSONVersion}"
              
               datas[0].spec.template.spec.containers[0]["image"]='de.icr.io/devops-tools/'+"${appname}"+'-fe:'+"${packageJSONVersion}"+"_"+"${timestamp_front}"
              datas[1].metadata.labels=['io.kompose.service': "${appname}"+'-fe']
              datas[1].metadata["name"]="${appname}"+'-fe'
               datas[1].spec.selector=['io.kompose.service': "${appname}"+'-fe']
              
                   sh 'rm front.yaml'
                writeYaml file: 'front.yaml', data: datas[0]
                sh 'rm servicefront.yaml > /dev/null'
               writeYaml file: 'servicefront.yaml', data: datas[1]

		def ingress = readYaml file:"ingress.yaml"
                	ingress.metadata["name"]= "${appname}"+'-fe'

                  url_for_gatling ="https://"+ingress.spec.rules[0]["host"]
              
          		sh 'rm ingress.yaml > /dev/null'
                writeYaml file: 'ingress.yaml', data: ingress
         
             }
           





         
             sh label: 'Login to ibmcloud', script: '''ibmcloud.exe login -u %IBM_ACCESS_KEY_ID% -p %IBM_SECRET_ACCESS_KEY% -r eu-de ''' 
           sh label: 'Login to ibm cr', script: '''ibmcloud.exe  cr login '''
           sh label: 'Configuring kubernetes', script: '''ibmcloud.exe ks cluster config -c c7pb9mkf09cf7vh8tmu0'''
            
                    dir("src/main/docker/backend") {
            sh 'kubectl apply -f back.yaml --namespace=develop'
            sh 'kubectl apply -f serviceback.yaml --namespace=develop'
         
                }
            dir("src/main/docker/frontend") {
            sh 'kubectl apply -f front.yaml --namespace=develop'
            sh 'kubectl apply -f servicefront.yaml --namespace=develop'
	    sh 'kubectl apply -f ingress.yaml --namespace=develop'
         
                }
      
        }
      
    }


         stage('Checking pods status') {
   

     script{
       def appname=readMavenPom().getArtifactId()
frontend_status=""
backend_status=""
for(int i = 0; i < 6; i++){
     

 if(frontend_status.contains("Running") && backend_status.contains("Running")){
    
     break
     
 }else{ 
     
                    frontend_status= sh(script: 'kubectl get pods --namespace=develop --selector=io.kompose.service='+"${appname}"+'-fe --no-headers -o custom-columns=":status.phase"' ,returnStdout: true).toString().split('"')[2] //TODO
               backend_status= sh(script: 'kubectl get pods --namespace=develop --selector=io.kompose.service='+"${appname}"+'-bff --no-headers -o custom-columns=":status.phase"' ,returnStdout: true).toString().split('"')[2] //TODO
               
     if(i==5){
        
         error('Los pods no estan ejecutandose correctamente, cancelando gatling...')

     }
     
 }

}

  
   


     }
   


 }

 

}
return this