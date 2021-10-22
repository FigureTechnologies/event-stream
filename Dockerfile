FROM openjdk:11

ADD ./docker-entrypoint.sh /docker-entrypoint.sh
ADD ./build/libs/*.jar /provenance-event-stream/provenance-event-stream.jar

ENTRYPOINT [ "/bin/bash", "-c", "./docker-entrypoint.sh /provenance-event-stream/provenance-event-stream.jar" ]
