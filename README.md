# A Refactor from the boot repository


## The Java Spring-Boot side of things
I thought I'd extract the kafka part from the [boot repository](https://github.com/stephenjohnlimb/boot/blob/master/src/main/microk8s/kafka-boot/README.md)
and also use 'gradle' as the main build mechanism.

I've extracted the `topic` names out of the source and added them to [application.properties](src/main/resources/application.properties), as below
```
...

# For the consumer
spring.kafka.consumer.topic=test-java-topic

# For the producer
spring.kafka.producer.topic=test-out-topic

...
```

I altered the [`SpringBootKafkaProducer`](src/main/java/com/tinker/kafka/SpringBootKafkaProducer.java)
so that the value of the topic could be pulled in from the application.properties file:
```
...

@Component
public class SpringBootKafkaProducer implements Consumer<Message> {

  @Value("${spring.kafka.producer.topic}")
  private String outgoingTopic;
  
...
```

I also altered the [`SpringBootKafkaConsumer`](src/main/java/com/tinker/kafka/SpringBootKafkaConsumer.java),
but this was a bit more involved - I don't really like this sort of 'jiggery-pokery' - the syntax of the expression is not that elegant.
```
...

@KafkaListener(topics = "#{'${spring.kafka.consumer.topic}'}")
  public void consume(ConsumerRecord<String, String> record) {

...

}

...
```

I found that I also needed to include some REST stuff to get the tomcat on port 8080 running and actuator
enabled for metrics and live-ness checks.

See [`IndexController`](src/main/java/com/tinker/kafka/IndexController.java) and [build.gradle](build.gradle),
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
resulting **artefacts** are pretty familiar to me.

I found 'gradle' to be pretty straight forward to get started with.
But I wanted to use Java 17 and so had to bump the version of gradle used.
See [gradle-wrapper.properties](gradle/wrapper/gradle-wrapper.properties) and specifically:
`distributionUrl=https\://services.gradle.org/distributions/gradle-7.4.2-bin.zip`.

I also had to modify [build.gradle](build.gradle) as below:
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

...
```

I needed the java and spring-boot plugins (I thought this might pull in my dependencies; but no).

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
**maven** has in terms of _compile, runtime, test_ for example. I could read the docs I suppose!

## Running the application
I wasn't too sure on how to 'use this gradle thing', but from within IntelliJ you can:
- Use the Gradle 'perspective window' right click and reload (essential if you edit build.gradle)
- Expand the tasks and pick a task to run - like 'assemble' or 'bootJar'

Note: from a terminal and with your prompt in the project root directory you can use:
`.\gradlew tasks` to see the list of tasks, then just choose the task like:
`.\gradlew bootJar` for example.

So by adding those 'plugins' for `java` and `spring-boot` you get a whole load of additional tasks.

To run the spring-boot application, just use Tasks->application->bootRun. Gradle will then build it for you
and run it. Or just `.\gradlew bootRun` (CTRL-C to terminate).

So going with one project, with one spring-boot application and gradle - is a nice simple flow.
It just makes sense to keep stuff for lots of microservices in this way. Your focus and way of working
is really, really simple. Easy to get new people started and a standard way of working.

'Just check out a repo from git, then use `.\gradlew ...'. Away you go! So this brings up the topic of
[monorepo versus polyrepo](https://blog.bitsrc.io/monorepo-vs-polyrepo-5-things-you-should-consider-897f3b588e70)!

### Repositories mono vs poly
In the past I've worked with both 'mono-repo' and 'poly-repo'; personally I think it comes down to the size of the
development organisation. If you are working in a fairly small team or not really fully embracing devops and microservices,
perhaps a 'mono-repo' is the way to go.

But for me, if you are scaling an organisation and employing micro-services with a real devops culture, I think
a 'poly-repo' can work well.

What I've found with the 'mono-repo' is that over time:
- You end up with lots of cross linkage (Java modules may help - but I doubt it)
- You end up not being able to migrate to newer Java versions with ease
- You really have to both compile up the whole repo and even deploy large chunks for testing
  - Because now with Java and dynamic loading/'Spring' - just compilation does not mean much
  - Team ownership of specific area can be a bit ambiguous
- You really need strong team(s) discipline (quite rare over a prolonged period of time)
- The surface area is 'big' - it's not always clear where to look for what
- The use of 'Spring' enables/facilitates laziness and inadvertent coupling
- A full checkout/clone of a large mono-repo takes some time and space

However, there are downsides to a 'poly-repo':
- Repository overload - where to look for what - but why are you looking if you're not in the team!
- You must have fully independent deployments of each 'microservice'
- It is necessary to pull common code into its own repo and manage versioned builds (much like open source)
- Separate components/libraries now need a roadmap and management.

But this later approach does 'scale' - when you think about it, this is the approach opensource employs.

The 'poly-repo' approach enforces true separation, it also enforces the explicit use of 'project-model' to
explicitly identify dependencies. In a Java centric microservice development team it can work very well.

Personally, having been around the 'bazaars' and various development teams/approaches and technologies all I
can say is; I've rarely found anywhere with prolonged strong discipline when it comes to source code management and
coupling (at scale).

Small, medium and large is comparatively easy - but very large is a whole different ball game.

So if you expect a 'full stack developer' to alter and edit a range of different code - then maybe a 'mono-repo'
is for you. But it depends on how you structure your teams to delivery functionality.

So for me if you plan to scale (really scale) - break things up - ensure each of the dependencies is 'owned' and has
a road map. I know people will say X has a 'mono-repo' and they make it work. Well maybe they have
strong discipline and self-control (does your company?).

If you're going down a Kafka route, clearly you have taken a decision to
really separate your processing to facilitate resilience, loose coupling, high throughput.
Why would you then couple all your code in a 'mono-repo'? What if you really needed to use a
Golang APi and language and migrate away from Java for a specific service?

With a 'poly-repo' approach - just start a new repo with Golang. Meet the same interfaces for the
Kafka topics (Avro maybe), run it all up and test it. Roll it out and delete the old Java repo.
But of course now your opportunity for code reuse through libraries (Java libraries) is nil.

This is what I mean by scaling/ownership - the team responsible for that service makes the decision.
You may then ask - but what about corporate standards/architecture/design patterns/re-use?

This is a natural 'friction' - 'architects'/'designers' want to enforce specific ways of working, but
then don't want to own the final running live service! These are 'macro-engineering' decisions and need
to be taken explicitly.

There is no way any other team could accidentally depend on any of your old Java repo code. But if it were in
a 'mono-repo' that possibility exists - So that old Java code would probably remain in place (just in case).

That is 'baggage' and technical debt. Few people ever have the courage in the future to delete that code,
especially with Spring style dynamic loading - sometimes just the presence (or not) of a class can change
behaviour (look at actuator - just add it to the dependencies and as if by magic it was used).

The 'mono-repo' vs 'poly-repo' has pros and cons, it depends on size of organisation/solution, politics,
governance, ownership, problem domain complexity/simplicity, availability/scalability/resilience/throughput of the
final solution. It will vary from organisation to organisation.

## Building a Dockerized Spring-Boot with Gradle
As I'd used [JIB](https://www.baeldung.com/jib-dockerizing) with my maven **boot** repo; I thought I'd try
it out with `gradle`.

So I added the following into [`build.gradle`](build.gradle):
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

The first bit just pulls in the extension, which gives a few key `jib` tasks, the
**ext** bit is just to define a variable/property (as I might make this switchable later).

Finally, the `jib` part is just the configuration as per the maven config in the **boot** repo.
- from - what image to base your image off
- allowInsecureRegistries - so we can push into our local microk8s registry for example
- to (image) - the url and name/version of the image we are going to produce.

Now run it with `.\gradlew jib` - that's it compiles, packages and pushes to the configured repo
with the configured name/version.

Now try:
```
curl http://192.168.64.2:32000/v2/gradle-kafka/tags/list
# You will see something like
# {"name":"gradle-kafka","tags":["1.0-SNAPSHOT"]}
```

You can bump the `version` in `build.gradle` and try again and will see an additional version in there.

i.e. ` {"name":"gradle-kafka","tags":["1.0.0","1.0-SNAPSHOT"]}`

OK so that was fairly easy, what about helm?

## Creating a helm chart and exposing values for application.properties

So the Spring-Boot Kafka application now reads some configuration from the
[application.properties](src/main/resources/application.properties) file. But DevOps guys aren't going
squirrel around inside the jar to alter that!

What we need to do is make the most of where Spring-Boot picks up the [application.properties](src/main/resources/application.properties)
from; it is aware that is might need to just look on the file system first and use those values.

So we can make the most of the Kubernetes `configMap` and map a volume in so that it looks like
an application.properties file on the file system.

Now if we use **helm** we can create a template and then push those values into the `configMap`
which will look like a file!

**So this is an important point; developers need to meet the DevOps deployers 'half-way'.**
What I mean by this is, we have to both develop the application, and it's configurability; but we need
to deliver that configurability in a standard way. This has to be ready for deployment.

### Create a helm chart
So `cd src\main\` and
issue `helm create helm`. Now we have a chart we can work with!

So alter [Chart.yaml](src/main/helm/Chart.yaml), add in [env.yaml](src/main/helm/templates/env.yaml)
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

Then update [values.yaml](src/main/helm/values.yaml) with:
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

This now makes it obvious to any deployment engineer, what can be configured via `values` within your application.

This is one of the key advantages of `helm`, the mechanism of configuration for any application is standardised!

Though the range of what can be configured and how it is addressed is flexible, the `values.yaml` is standard for
all applications.

### Deploy the application!

To deploy, we need to build first and then use helm install.

At present these are two separate activities (so I need to look at that), I also would like
to have the helm `Chart.yaml` appVersion aligned with the `version` in the `build.gradle`.

Maybe I'll look at that later!

#### Build and Push Docker Image
Now if you've run `.\gradlew jib`; you will have created the containerised application `gradle-kafka:1.0-SNAPSHOT`
and pushed it to `192.168.64.2:32000`. So that will exist as `localhost:32000/gradle-kafka:1.0-SNAPSHOT`
as the `Chart.yaml` has `appVersion` at `1.0-SNAPSHOT`.

#### Deploy it via helm
Now run just check the helm chart is OK with:
- `helm lint .\helm`
- `helm --dry-run --debug install my-gradle-kafka .\helm`

Check the values that would be employed in the `configMap` are pulled through OK.

If all is OK (and your indentation isn't out of whack), just deploy it:
- `helm install my-gradle-kafka .\helm`

You can then use `kubectl get services` and `kubectl get pods`.

### Now test it out again!

So from two terminal you can use:
- `kafkacat -P -b 192.168.64.90:9094 -t test-out-topic3 -K :`
- `kafkacat -C -b 192.168.64.90:9094 -t test-out-topic3 -K :`

If all is running well, then the key/values you enter in `kafkacat -P ...` should
come out with the values reversed in terminal window `kafkacat -C`.

## More improvements
I've yet to tie up the gradle `version` and the helm Chart `appVersion`. I would also like
to use the mechanisms in gradle to update the helm Chart `appVersion` and also do a full
build and deploy with a single command.

What I needed was a way to effectively replace in a few place-holders in the helm chart. Those values
coming from the gradle configuration. So I found `org.unbroken-dome.helm` and added that plugin.
```
plugins {
    id 'java'
    id 'org.springframework.boot' version '2.7.1'
    id 'com.google.cloud.tools.jib' version '3.2.1'
    id "org.unbroken-dome.helm" version "1.7.0"
}
```

Now I just had to revisit my helm config and put in a few place-holders, then add a bit more configuration
to [build.gradle](build.gradle).

```
...

ext {
    //Default of where we will push the resulting jib generated docker image.
    pushDockerRepository = "192.168.64.2:32000"
    //This is where the helm deployment in K8s will pull image from.
    pullDockerRepository = "localhost:32000"
    helmChartVersion = '1.0.0'
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
        image = "${project.pushDockerRepository}/${rootProject.name}:${version}"
    }
}

// Use the helm plug in to also build and populate the helm chart parts
//i.e. yes template the template - but only for a few items.
helm {
    filtering {
        //But this is where K8s should pull the image from
        values.put 'imageRepository', project.pullDockerRepository
        values.put 'imageTag', version
        values.put 'projectName', rootProject.name
        values.put 'helmChartVersion', project.helmChartVersion
    }
}

...

```

I also updated `Chart.yaml`:
```
apiVersion: v2
# Expect org.unbroken-dome.helm to populate these placeholders, see build.gradle.
name: ${projectName}
description: A Helm chart for ${projectName}

type: application

# The version number of the chart
version: ${helmChartVersion}

# The version number of the docker application image.
# Always best to use double quotes.
appVersion: "${imageTag}"
```

The idea here is to collect all the normal variables for versions and the like in the `build.gradle` file
and then use `gradle` and `jib`/`broken-dome` to effectively update those values in our helm template files.

So now, to build I can use:
- ./gradlew jib
- ./gradlew helmPackage

Then I've got both a build of the Java and Docker image and the build of my helm templates, so now I can deploy locally:
- helm install my-gradle-kafka ./build/helm/charts/gradle-kafka

Now clearly it is possible/desirable to take this further and publish the resulting
artefacts to specific repositories.

Also I could combine the whole build into a single command with `gradle`.

Maybe I'll look at that later.

## Summary
If you are using Spring-Boot, Docker and Kubernetes; Gradle and its `Jib`/`unbroken dome` plugins
makes life **really easy**.

If you have a large set of applications to deploy, you need a standard way of deploying and
helm gives you that capability.

### So what's really in this repo
This repo would really just be one of many in a 'poly-repo', it's got:
- A standard `gradle` build file that produces a docker image and a helm chart
- The resulting values.yaml tell an engineer what can and should be configured at deployment time
- Some standard Spring Java Kafka annotated [classes](src/main/java/com/tinker/kafka)
- Some very basic [business](src/main/java/com/tinker/business) functionality (string reversing!)
- It has prometheus monitoring components built in

It should also really have an associated `grafana` configuration for monitoring.
There should also be a range of tests, both the business logic tests and the Kafka messaging
serialization/deserialization tests.

See [Poison Pill](https://www.confluent.io/en-gb/blog/spring-kafka-can-your-kafka-consumers-handle-a-poison-pill/) on why
you need to run checks on incoming messages.

If you are seriously using Kafka, you will probably want to have a range of [Fuzzing](https://owasp.org/www-community/Fuzzing)
messages or code to create a range of fuzzing messages.

This will probably be:
- Just pseudo random stuff that should fail at deserialization
- Valid serialised data but with a wide range of invalid values
- For AVRO, you'd probably want to generate a vaid AVRO structure but with lots of bad combinations of data.

Again, for serious use of messaging, you should deploy your Kafka component that processes incoming messages
in isolation. So this is not a unit-test or an integration-test or even an end-to-end-test.
But a long-running (like forever running) isolated deployment, that just has an infinite stream
of fuzzed messages being injected. Just keep it going - you should never get a failure in your logs.
Your dead letter queue should just be filled with all the fuzzed messages as your robust application
rejects them. Your valid processing queue should remain empty, after all; all the input is supposed to be
in error.

But in terms of 'getting your head around it', its surface area is small, the dependencies are documented
and obvious [see build.gradle](build.gradle). No other project could every depend on our 
[StringReverser](src/main/java/com/tinker/business/internal/StringReverser.java). It is a hidden low level implmentation
detail.

Now imagine giving a task to a new developer or contractor:
"Enhance the service to not only reserve the incoming value, but also UpperCase the first letter in the resulting output".

If I'd elected to use AVRO, then we'd have needed to do some consumer/producer checks as well.

It is pretty easy to provide the url of the 'repo'. Easy for that developer to, check-out and build/run the
application. Updates the test (if I'd added some), bump the application and chart version and commit it.

Task done! Now what about the wider selection and running of this new version?

Well that developer does not need to work about that, the developer can just get on with the next task. The
selection of when to roll out and check fully end-to-end can be made elsewhere and at the appropriate time.

Note, there is virtually no chance of having 'merge conflicts', there is no danger of altering that
reversing code because something else uses it
(either directly or by constructing a Spring bean name from strings and doing a 'Class.forName' or 'BeanFactory.getBean').

This is one of the main reasons I prefer a 'poly-repo' approach. Finding stuff that is dependent on some code you're
about to change is easy. This is also one of the reasons I only use Spring in moderation and in a very limited manner.