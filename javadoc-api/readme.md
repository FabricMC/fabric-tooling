### JWT Key gen

```bash
openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:secp384r1 -out jwt-private.pem
openssl pkey -in jwt-private.pem -pubout -out jwt-public.pem
```
