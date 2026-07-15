# OpenAPI Contract

The Spring Boot application exposes the live OpenAPI document at:

- `http://localhost:8080/v3/api-docs`
- `http://localhost:8080/swagger-ui/index.html`

## Local workflow

Run this command from the repository root after changing a controller or DTO:

```powershell
npm run api:sync
```

It runs the OpenAPI contract test with the test profile, exports the generated
document to `docs/openapi/openapi.json`, and generates TypeScript declarations
at `frontend/packages/types/src/generated/openapi.d.ts`.

To regenerate only the TypeScript declarations from the committed document:

```powershell
npm run api:generate
```

To verify generated declarations are up to date:

```powershell
npm run api:check
```

The generated declarations are a migration bridge for the existing handwritten
types. New frontend code can import the generated `paths` and `components`
types without changing the existing API client in one step.
