# 📘 SYSTEM DESIGN SPEC (Cursor AI Template)

## 1. 🧩 GOAL

Create a plan to build a dynamic entity builder backend api, for a modern highly extendable
low code, multi-tenanted erp system.

Example:

> Build a scalable backend system for managing dynamic entities and workflows.

---

## 2. 🎯 OBJECTIVES

* Should be able to create entities with fields and properties 
* Should be able to create relationship between entities
* Any tenant should be able to extend existing entities
* Should use iam security we built earlier for security
* OpenAPI + an additional machine-readable agent guide designed for LLM tool use (stable schemas, examples, error shapes, workflows).
* Propose if actual data saving and handling for entities should be a separate module/service
* Allow saving of actual entity data. entity_records, records_links
* Allow should design security around PII, using pii_vault table
* Propose if dynamic form builder will also in this module
* Form layouts
* Propose how we handle data for reporting? send it to clickhouse? etc.
* 


---

## 3. 🚫 OUT OF SCOPE

Define what should NOT be built.

* UI

---

## 4. ⚙️ TECH STACK

Specify exact technologies (important for Cursor accuracy).
* Same as the iam stack
* Language:
* Framework:
* Database:
* Build Tool:
* Other Libraries:

---

## 5. 🏗 ARCHITECTURE

Describe system structure.

* Style (Microservices):
* Layers:

    * Controller
    * Service
    * Repository
* Key Design Patterns:
* Communication (REST, gRPC, events):

---

## 6. 📦 DATA MODEL

Define core entities.

### Entity: <Name>

* field1: type
* field2: type

### Entity: <Name>

* field1: type
* field2: type

---

## 7. 🔁 CORE FLOWS / USE CASES

### Flow 1: <Name>

1. Step 1
2. Step 2
3. Step 3

### Flow 2: <Name>

1. Step 1
2. Step 2

---

## 8. 🔌 API DESIGN

### Endpoint: <Name>

* Method:
* URL:
* Request:
* Response:

---

## 9. 🔐 CONSTRAINTS

* Constraint 1
* Constraint 2
* Constraint 3

---

## 10. ⚡ NON-FUNCTIONAL REQUIREMENTS

* Performance:
* Scalability:
* Security:
* Reliability:

---

## 11. 📁 PROJECT STRUCTURE

Example:

```
src/
 ├── controller/
 ├── service/
 ├── repository/
 ├── model/
```

---

## 12. 🧪 TESTING STRATEGY

* Unit tests:
* Integration tests:
* Edge cases:

---

## 13. 🚀 EXPECTED OUTPUT FROM CURSOR

Specify exactly what Cursor should generate:

* [ ] Architecture explanation
* [ ] Class design
* [ ] Database schema
* [ ] API implementation
* [ ] Sample code

---

## 14. 📌 NOTES / SPECIAL INSTRUCTIONS

Add any important instructions for Cursor:

* Prefer clean architecture
* Avoid over-engineering
* Follow best practices
* Use modern standards

---

## 15. 🧠 EXECUTION INSTRUCTIONS (FOR CURSOR)

IMPORTANT:

* First, analyze and propose a design.
* Do NOT generate code immediately.
* Wait for confirmation before implementation.
* Break solution into steps.

---
