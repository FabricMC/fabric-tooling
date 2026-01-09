# API Documentation

## Authentication Flow

1. **Initial OAuth Request**: Client requests an OAuth URL for their chosen platform (GitHub or Discord)
2. **OAuth Landing**: User completes OAuth flow and is redirected to the landing endpoint
3. **Refresh Token**: A secure, HTTP-only cookie containing a refresh token is issued (valid for 7 days)
4. **Access Token**: Client exchanges refresh token for short-lived access tokens
5. **Access Validation**: Access tokens are used to authenticate API requests

---

## Endpoints

### Request Discord OAuth URL

**Endpoint**: `GET /v1/auth/discord`

**Summary**: Request a Discord OAuth authorization URL

**Authentication**: None (Open)

**Response**:
- **Status**: 200 OK
- **Content-Type**: application/json

**Response Body**:
```json
{
  "url": "https://example.com"
}
```

---

### Request GitHub OAuth URL

**Endpoint**: `GET /v1/auth/github`

**Summary**: Request a GitHub OAuth authorization URL

**Authentication**: None (Open)

**Response**:
- **Status**: 200 OK
- **Content-Type**: application/json

**Response Body**:
```json
{
  "url": "https://github.com/login/oauth/authorize?..."
}
```

---

### GitHub OAuth Landing

**Endpoint**: `GET /v1/auth/github/landing`

**Summary**: GitHub OAuth callback endpoint - handles the redirect after user authorizes

**Authentication**: None (Open)

**Query Parameters**:

| Parameter | Type   | Required | Description                          |
|-----------|--------|----------|--------------------------------------|
| code      | string | Yes      | The OAuth authorization code from GitHub |
| state     | string | Yes      | The OAuth state parameter for CSRF protection |

**Response**:
- **Status**: 302 Found (Redirect)
- **Set-Cookie**: `refreshToken` (HTTP-only, Secure, SameSite=Strict)
  - **Path**: `/v1/auth/refresh`
  - **Max-Age**: 7 days (604,800 seconds)

**Cookie Details**:
```
refreshToken=<token-value>; 
Path=/v1/auth/refresh; 
Max-Age=604800; 
Secure; 
HttpOnly; 
SameSite=Strict
```

---

### Refresh Access Token

**Endpoint**: `POST /v1/auth/refresh`

**Summary**: Exchange a refresh token for a new access token

**Authentication**: Refresh Token (Cookie)

**Cookies**:

| Name         | Required | Description                               |
|--------------|----------|-------------------------------------------|
| refreshToken | Yes      | The refresh token issued during OAuth login |

**Response**:
- **Status**: 200 OK
- **Content-Type**: application/json

**Response Body**:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Error Responses**:
- **401 Unauthorized**: Invalid or expired refresh token

---

### Check Access Token Validity

**Endpoint**: `GET /v1/auth/check`

**Summary**: Validate an access token

**Authentication**: Access Token (Bearer)

**Headers**:

| Name          | Required | Description                    |
|---------------|----------|--------------------------------|
| Authorization | Yes      | Bearer token: `Bearer <access-token>` |

**Response**:
- **Status**: 200 OK - Access token is valid
- **Status**: 401 Unauthorized - Missing or invalid access token

---

### Get Javadoc

**Endpoint**: `POST /v1/javadoc/{version}`

**Summary**: Request the Javadoc for a given class and its inner classes

**Authentication**: Access Token (Bearer)

**Path Parameters**:

| Parameter | Type   | Required | Description                    | Example |
|-----------|--------|----------|--------------------------------|---------|
| version   | string | Yes      | The Minecraft version          | 26.1    |

**Headers**:

| Name          | Required | Description                    |
|---------------|----------|--------------------------------|
| Authorization | Yes      | Bearer token: `Bearer <access-token>` |

**Request Body**:
```json
{
  "className": "net/minecraft/client/Minecraft"
}
```

**Response**:
- **Status**: 200 OK
- **Content-Type**: application/json

**Response Body**:
```json
{
  "data": {
    "net/minecraft/client/Minecraft": {
      "value": "The main Minecraft client class",
      "methods": {
        "getInstance()Lnet/minecraft/client/Minecraft;": "Returns the singleton instance of the Minecraft client",
        "getWindow()Lcom/mojang/blaze3d/platform/Window;": "Gets the game window"
      },
      "fields": {
        "instance:Lnet/minecraft/client/Minecraft;": "The singleton instance of Minecraft",
        "fps:I": "Current frames per second"
      }
    },
    "net/minecraft/client/Minecraft$Inner": {
      "value": "An inner class of Minecraft"
    }
  }
}
```

**Error Responses**:
- **401 Unauthorized**: Missing or invalid access token
- **404 Not Found**: Javadoc not found for the given class

---

### Update Javadoc

**Endpoint**: `PATCH /v1/javadoc/{version}`

**Summary**: Update the Javadoc for a given class, method, or field

**Authentication**: Access Token (Bearer)

**Path Parameters**:

| Parameter | Type   | Required | Description                    | Example |
|-----------|--------|----------|--------------------------------|---------|
| version   | string | Yes      | The Minecraft version          | 26.1    |

**Headers**:

| Name          | Required | Description                    |
|---------------|----------|--------------------------------|
| Authorization | Yes      | Bearer token: `Bearer <access-token>` |

**Request Body (Class Documentation)**:
```json
{
  "className": "net/minecraft/client/Minecraft",
  "documentation": "The main Minecraft client class.\nHandles game initialization and the main game loop."
}
```

**Request Body (Method Documentation)**:
```json
{
  "className": "net/minecraft/client/Minecraft",
  "target": {
    "type": "method",
    "name": "getInstance",
    "descriptor": "()Lnet/minecraft/client/Minecraft;"
  },
  "documentation": "Returns the singleton instance of the Minecraft client.\n\n@return the Minecraft instance"
}
```

**Request Body (Field Documentation)**:
```json
{
  "className": "net/minecraft/client/Minecraft",
  "target": {
    "type": "field",
    "name": "fps",
    "descriptor": "I"
  },
  "documentation": "The current frames per second counter"
}
```

**Response**:
- **Status**: 200 OK - Javadoc updated successfully

**Error Responses**:
- **400 Bad Request**: Invalid target type
- **401 Unauthorized**: Missing or invalid access token
