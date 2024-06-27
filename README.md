# Arrowhead Authorization Ruler

Updates the authorization rules within a local cloud based on systems' metadata and service definitions using the SysOp certificate of a local cloud.

## Requiriments

- Java 11 or higher
- Your SysOp certificate
- A rules.json file

#### How to run 

- Create your rules.json file
- Adjust the parameter values in application.properties file (Like Service Registry address, certificate password etc...)
- Run `java -jar ah-auth-ruler-<version>.jar <path/to/your/rules.json>`

**Data model rules.json:**

```
[
   {
      "consumer":"<sys-metadata-key>=<sys-metadata-value>" || "systemName",
      "provider":"<sys-metadata-key>=<sys-metadata-value>" || "systemName",
      "service":"<servicedefinition>",
      "interfaces":[
         "<interface-name>"
      ]
   }
]
```
_No interfaces defined means HTTP-SECURE-JSON and HTTP-INSECURE-JSON as defaults._
