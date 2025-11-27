FROM eclipse-temurin:21-jdk-alpine
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
#//어떤이미지 기반인지
#//컨테이너 이미지를 어떻게 만들 수 있느냐
#//그래서 도커 이미지를 어떻게 실행할건데?