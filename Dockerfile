FROM openjdk:8u181-jre

WORKDIR /app
ARG JAR_FILE

ADD target/${JAR_FILE} .

ENV EXECUTABLE=${JAR_FILE}
CMD java -Djava.net.preferIPv4Stack=true -jar ${EXECUTABLE}
