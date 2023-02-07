FROM openjdk:11

ADD ./docker-entrypoint.sh /docker-entrypoint.sh
ADD ./build/libs/*.jar /event-stream/event-stream.jar

ENTRYPOINT [ "/bin/bash", "-c", "./docker-entrypoint.sh /event-stream/event-stream.jar" ]
