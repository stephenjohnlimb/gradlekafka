## A Refactor from the boot repository

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
### Gradle configuration
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

### Running the application
I wasn't too sure on how to 'use this gradle', but from within IntelliJ you can:
- Use the Gradle 'perspective window' right click and reload (essential if you edit build.gradle)
- Expand the tasks and pick a task to run - like 'assemble' or 'bootJar'

So by adding those 'plugins' for `java` and `spring-boot` you get a whole load of additional tasks.

To run the spring-boot application, just use Tasks->application->bootRun. Gradle will then build it for you
and run it.

So going with the flow of one project, with one spring-boot application and gradle - is a nice simple flow.
It just makes sense to keep stuff for lots of microservices in this way. Your focus and way of working
is really, really simple. Easy to get new people started and a standard way of working.

### Building a Dockerized Spring-Boot with Gradle
TODO
