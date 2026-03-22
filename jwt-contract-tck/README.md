# jwt-contract-tck

Small **test-only fixture** library: constants and [`JwtTckTokenFactory`](src/main/java/com/erp/jwt/tck/JwtTckTokenFactory.java) mint JWTs with the same claim shape as IAM.

- **Contract doc:** [`design/JWT_access_token_contract.md`](../design/JWT_access_token_contract.md)
- **Consumers:** `testImplementation project(':jwt-contract-tck')` + `JwtAccessTokenContractTest` in IAM, entity-builder, global-search.

```bash
./gradlew :jwt-contract-tck:test
```
