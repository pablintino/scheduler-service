# Scheduler Service

This microservices acts as a distributed task scheduler that admits cron expressions or single time tasks to be "notified" with the payload given during task creation.  
The microservice has a REST API that exposes simple CRUD endpoints to schedule tasks that will be notified to the calling microservice when the cron expresion triggers or when the task single-shot time arrives. When task time is reached or the cron expression triggers, the scheduler, based on the notification method selected during task creation, notifies the calling service my AMQP (Rabbit) or by a simple REST call.

## How to build and test

Requirement for building and testing the service:
- Jdk 11
- Maven >  3.8.X
- Docker (for IT testing)

```bash
mvn clean verify
mvn clean verify -P it-test  # Will run IT tests using contenerized database and Rabbit
```