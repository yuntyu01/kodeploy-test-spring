# our base build image
FROM maven:3.9-eclipse-temurin-11 AS maven

# copy the project files
COPY ./pom.xml ./pom.xml

# build all dependencies
RUN mvn dependency:go-offline -B

# copy your other files
COPY ./src ./src

# build for release
RUN mvn package -DskipTests

# our final base image
FROM eclipse-temurin:11-jre

# set deployment directory
WORKDIR /my-project

# copy over the built artifact from the maven image
COPY --from=maven target/springboot-starterkit-mysql-1.0.jar ./

# set the startup command to run your binary
CMD ["java", "-jar", "./springboot-starterkit-mysql-1.0.jar"]
