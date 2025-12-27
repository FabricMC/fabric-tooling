### Running

Create `config.json` in the working directory with the following content:

```json
{
  "apiUrl": "http://localhost:8080",
  "jwt": {
    "issuer": "localhost",
    "privateKey": "jwt-private.pem",
    "publicKey": "jwt-public.pem"
  },
  "githubOAuth": {
    "clientID": "123",
    "clientSecret": "456"
  }
}
```

Run:

```bash
openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:secp384r1 -out jwt-private.pem
openssl pkey -in jwt-private.pem -pubout -out jwt-public.pem
```
