# A Refactor from the boot repository


## The Java Spring-Boot side of things
I thought I'd extract the kafka part from the [boot repository](https://github.com/stephenjohnlimb/boot/blob/master/src/main/microk8s/kafka-boot/README.md)
and also use 'gradle' as the main build mechanism.

I've extracted the `topic` names out of the source and added them to [application.properties](resources/application.properties).
```
# For the consumer
spring.kafka.consumer.topic=test-java-topic

# For the producer
spring.kafka.producer.topic=test-out-topic
```

I altered the [`SpringBootKafkaProducer`](java/com/tinker/kafka/SpringBootKafkaProducer.java)
so that the value of the topic could be pulled in from the application.properties file:
```
@Component
public class SpringBootKafkaProducer implements Consumer<Message> {

  @Value("${spring.kafka.producer.topic}")
  private String outgoingTopic;
...
```

I also altered the [`SpringBootKafkaConsumer`](java/com/tinker/kafka/SpringBootKafkaConsumer.java),
but this was a bit more involved - I don't really like this sort of 'jiggery-pokery' - the syntax of the expression is not that elegant.
```
@KafkaListener(topics = "#{'${spring.kafka.consumer.topic}'}")
  public void consume(ConsumerRecord<String, String> record) {
...
}
```

I found that I also needed to include some REST stuff to get the tomcat on port 8080 running and actuator
enabled for metrics and live-ness checks.

See [`IndexController`](java/com/tinker/kafka/IndexController.java) and [build.gradle](build.gradle),
specifically:
```
dependencies {
...
    //Actuator is for the metrics
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    //This is for the additional output for prometheus - see application.properties for details.
    implementation 'io.micrometer:micrometer-registry-prometheus'

    //We need at least and index controller to kicking the port 8080 stuff and get the actuator running.
    implementation 'org.springframework.boot:spring-boot-starter-web'
...
}
```
## Gradle configuration
I've never really done any gradle before, but have used:
- make
- imake
- ant
- ivy
- maven

So the general concepts of **source files**, **dependencies** and
resulting **artefacts** is pretty familiar to me.

I found 'gradle' to be pretty straight forward to get started with.
But I wanted to use Java 17 and so had to bump the version of gradle I use.
See [gradle-wrapper.properties](gradle/wrapper/gradle-wrapper.properties) and specifically:
`distributionUrl=https\://services.gradle.org/distributions/gradle-7.4.2-bin.zip`.

I also had to modify [build.gradle](build.gradle):
```
plugins {
    id 'java'
    id "org.springframework.boot" version "2.7.1"
}

group 'com.tinker'
version '1.0-SNAPSHOT'

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
```

I needed the java and spring-boot plugins (I thought this might pull in my dependencies but no).

So far, quite similar to maven, but more concise (I quite like it).

Then I just added a few more dependencies:
```
dependencies {
    implementation platform('org.springframework.boot:spring-boot-dependencies:2.7.1')
    implementation 'org.springframework.boot:spring-boot-starter'

    //Actuator is for the metrics
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    //This is for the additional output for prometheus - see application.properties for details.
    implementation 'io.micrometer:micrometer-registry-prometheus'

    //We need at least and index controller to kicking the port 8080 stuff and get the actuator running.
    implementation 'org.springframework.boot:spring-boot-starter-web'

    //Specific kafka stuff
    implementation 'org.springframework.kafka:spring-kafka'
    implementation 'org.apache.kafka:kafka-clients:3.2.0'

    //Just for our testing
    testImplementation 'org.junit.jupiter:junit-jupiter-api:5.8.2'
    testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.8.2'
}
```

I wasn't sure what this `implementation` meant at first, but then it dawned on me; it's the same sort of thing
**maven** has in terms of _compile, runtime, test_ for example.

## Running the application
I wasn't too sure on how to 'use this gradle', but from within IntelliJ you can:
- Use the Gradle 'perspective window' right click and reload (essential if you edit build.gradle)
- Expand the tasks and pick a task to run - like 'assemble' or 'bootJar'

So by adding those 'plugins' for `java` and `spring-boot` you get a whole load of additional tasks.

To run the spring-boot application, just use Tasks->application->bootRun. Gradle will then build it for you
and run it.

So going with the flow of one project, with one spring-boot application and gradle - is a nice simple flow.
It just makes sense to keep stuff for lots of microservices in this way. Your focus and way of working
is really, really simple. Easy to get new people started and a standard way of working.

## Building a Dockerized Spring-Boot with Gradle
As I'd used [JIB](https://www.baeldung.com/jib-dockerizing) with my **boot** repo; I thought I'd try
it out with `gradle`.

So I added the following into `build.gradle`:
```
plugins {
    id 'java'
    id 'org.springframework.boot' version '2.7.1'
    id 'com.google.cloud.tools.jib' version '3.2.1'
    ...
    }
    
...

ext {
    //Default of where we will push the resulting jib generated docker image.
    targetDockerRepository = "192.168.64.2:32000"
}
...

jib {
    allowInsecureRegistries = true
    from {
        image = 'openjdk:17-jdk-alpine'
    }

    to {
        //Double quotes important here like bash shell, single quotes are literal
        //but double quotes allow variable expansion.
        image = "${targetDockerRepository}/${rootProject.name}:${version}"
    }
}
...    

```

The first bit just pulls in the extension, which gives a few key `'jib` tasks, the
**ext** bit is just to define a variable (as I might make this switchable later).

Finally, the `jib` part is just the configuration as per the maven config in the **boot** repo.
- from - what image to base your image off
- allowInsecureRegistries - can we push into our local microk8s registry for example
- to (image) - the url and name/version of the image we are going to produce.

Now run it with `.\gradlew jib` - that's it compiles, packages and pushes to the configured repo
with the configured name/version.

Now try:
```
curl http://192.168.64.2:32000/v2/gradle-kafka/tags/list
# You will see something like
# {"name":"gradle-kafka","tags":["1.0-SNAPSHOT"]}
```

You can dump the `version` in `build.gradle` and try again and will see an additional version in there.

i.e. ` {"name":"gradle-kafka","tags":["1.0.0","1.0-SNAPSHOT"]}`

OK so that was fairly easy, what about helm?

## Creating a helm chart and exposing values for application.properties

So the Spring-Boot Kafka application now does read some configuration from the
[application.properties](resources/application.properties) file. But DevOps guys aren't going
squirrel around inside the jar to alter that!

What we need to do is make the most of where Spring-Boot pick up the application.properties from;
it is aware that is might need to just look on the file system first and use those values.

So we can make the most of the Kubernetes `configMap` and map a volume in so that it looks like
an application.properties file on the file system.

Now if we use **helm** we can create a template and then push those values into the `configMap`
which will look like a file!

### Create a helm chart
Make a directory in this repo called `src\main\helm` then `cd src\main\helm` and
issue `helm create gradle-kafka`. Now we have a chart we can work with!

So alter [Chart.yaml](helm/gradle-kafka/Chart.yaml), add in [env.yaml](helm/gradle-kafka/templates/env.yaml)
with:
```
apiVersion: v1
kind: ConfigMap
metadata:
  name: {{ .Release.Name }}-configmap
data:

  # Expose the spring-boot application configuration,
  # so that is can be altered and adjusted
  application.properties: |
    server.shutdown=graceful
    management.endpoints.web.exposure.include=health,info,prometheus

    spring.kafka.bootstrap-servers={{ .Values.spring.kafka.bootstrapServers }}

    # For the consumer
    spring.kafka.consumer.topic={{ .Values.spring.kafka.consumer.topic }}
    spring.kafka.consumer.group-id={{ .Values.spring.kafka.consumer.groupId }}
    spring.kafka.consumer.client-id={{ .Values.spring.kafka.consumer.clientId }}
    spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
    spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer

    # For the producer
    spring.kafka.producer.topic={{ .Values.spring.kafka.producer.topic }}
    spring.kafka.producer.client-id={{ .Values.spring.kafka.producer.clientId }}
    spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
    spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer
```

Then update [values.yaml](helm/gradle-kafka/values.yaml) with:
```
...

image:
  repository: localhost:32000/gradle-kafka

...

service:
  type: LoadBalancer
  port: 80
  targetPort: 8080
  
...

# Expose the configurable properties.
spring:
  kafka:
    bootstrapServers: "192.168.64.90:9094"
    consumer:
      topic: "test-java-topic3"
      groupId: "a-consumer-group"
      clientId: "a-processor"
    producer:
      topic: "test-out-topic3"
      clientId: "a-processor"
      
```

We need to tell helm which repository to pull the application from `localhost:32000/gradle-kafka` in this case,
as it is coming from the microk8s registry itself.

We also need to expose on target port 8080 for tomcat.

Then we move on to the values we are going to inject into the application.properties via the
`configMap`. Hopefully you can see how the values defined here correlate to the values in the `env.yaml`.

### Deploy the application!

To deploy, we need to build first and then use helm install.

At present these are two separate activities (so I need to look at that), I also would like
to have the helm `Chart.yaml` appVersion aligned with the `version` in the `build.gradle`.

Maybe I'll look at that later!

#### Build and Push Docker Image
Now if you've run `.\gradlew jib` you will have created the containerised application `gradle-kafka:1.0-SNAPSHOT`
and pushed it to `192.168.64.2:32000`. So that will exist as `localhost:32000/gradle-kafka:1.0-SNAPSHOT`
as the `Chart.yaml` has `appVersion` at `1.0-SNAPSHOT`.

#### Deploy it via helm
Now run just check the helm chart is Ok with:
- `helm lint .\gradle-kafka`
- `helm --dry-run --debug install my-gradle-kafka .\gradle-kafka`

Check the values that would be employed in the `configMap` are pulled through OK.

If all is OK (and your indentation isn't out of whack), just deploy it:
- `helm install my-gradle-kafka .\gradle-kafka`
You can then use `kubectl get services` and `kubectl get pods`.

### Now test it out again!

So from two terminal you can use:
- `kafkacat -P -b 192.168.64.90:9094 -t test-out-topic3 -K :`
- `kafkacat -C -b 192.168.64.90:9094 -t test-out-topic3 -K :`

If all is running well, then the key/values you enter in `kafkacat -P ...` should
come out with the values reversed in terminal window `kafkacat -C`.

## Summary
If you are using Spring-Boot, Docker and Kubernetes; Gradle and its `Jib` plugin
make life really easy.

I've yet to tie up the gradle `version` and the helm Chart `appVersion`. I would also like
to use the mechanisms in gradle to update the helm Chart `appVersion` and also do a full
build and deploy with a single command.

That is still to do!