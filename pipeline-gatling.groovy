def start(String url){

     stage('Checkout Source') {
      
      script{
      def git_command=  git "https://github.com/eduardojc-met/gatling.git"
      
      }
       
    }




        stage("Insert project url") {

            
           dir("src/test/java/computerdatabase/"){

                  script{
                 def javaFile = readFile "FoundationSimulation.java"
            javaFile.replaceAll("https*", "${url}"+")")
                sh "rm FoundationSimulation.java"
                 writeFile file: "FoundationSimulation.java", text: javaFile

            }
           } 

         
           
        }

        stage("Gatling run") {
            script {
                sh 'mvn gatling:test -Dgatling.simulationClass=io.gatling.demo.FoundationSimulation'
            }
            gatlingArchive()
            
        }






}



return this