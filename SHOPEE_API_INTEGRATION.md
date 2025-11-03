# Integração com API GraphQL da Shopee

## Resumo
Implementação da integração com a API de afiliados da Shopee para coletar automaticamente nome do produto e preço a partir de links shortlink.

## Credenciais
- **AppID**: 18344110677
- **Secret**: BEWYLTPASZH2TJXVMQUQVGU3YBSYX64T
- **Endpoint**: https://open-api.affiliate.shopee.com.br/graphql

## Fluxo de Autenticação

### 1. Coleta de Timestamp
```kotlin
val timestamp = System.currentTimeMillis() / 1000  // Unix timestamp em segundos
```

### 2. Construção do Payload GraphQL
```kotlin
val query = "{ productOfferV2(listType: 0, sortType: 5, productCatId:101803, limit:1,isAMSOffer: true) { nodes { commissionRate commission imageUrl price productLink offerLink productName } }}"
val payload = buildJsonObject { put("query", query) }.toString()
```

### 3. Geração da Assinatura SHA256
**Fator de assinatura**: `AppId + Timestamp + Payload + Secret`

```kotlin
val factor = "$APP_ID$timestamp$payload$SECRET"
val digest = MessageDigest.getInstance("SHA-256")
val hashBytes = digest.digest(factor.toByteArray(Charsets.UTF_8))
val signature = hashBytes.joinToString("") { "%02x".format(it) }
```

**IMPORTANTE**: Usar SHA256 simples, **NÃO** HMAC-SHA256.

### 4. Construção do Header de Autorização
```kotlin
Authorization: SHA256 Credential={AppID}, Timestamp={timestamp}, Signature={signature}
```

Exemplo:
```
Authorization: SHA256 Credential=18344110677, Timestamp=1577836800, Signature=dc88d72feea70c80c52c3399751a7d34966763f51a7f056aa070a5e9df645412
```

## Request HTTP

### Método
POST

### Content-Type
application/json

### Body
```json
{
  "query": "{ productOfferV2(listType: 0, sortType: 5, productCatId:101803, limit:1,isAMSOffer: true) { nodes { commissionRate commission imageUrl price productLink offerLink productName } }}"
}
```

## Response Esperada

### Sucesso (HTTP 200)
```json
{
  "data": {
    "productOfferV2": {
      "nodes": [
        {
          "productName": "Nome do Produto",
          "price": "99.90",
          "commissionRate": "0.05",
          "commission": "4.99",
          "imageUrl": "https://...",
          "productLink": "https://...",
          "offerLink": "https://..."
        }
      ]
    }
  }
}
```

### Erro
```json
{
  "errors": [
    {
      "message": "Descrição do erro"
    }
  ]
}
```

## Estrutura de Dados Kotlin

```kotlin
@Serializable
data class ShopeeResponse(
    val data: ShopeeData?
)

@Serializable
data class ShopeeData(
    @SerialName("productOfferV2")
    val productOffer: ProductOffer
)

@Serializable
data class ProductOffer(
    val nodes: List<ProductNode>?
)

@Serializable
data class ProductNode(
    val productName: String,
    val price: String? = null,
    val priceMin: String? = null,
    val commissionRate: String? = null,
    val commission: String? = null,
    val imageUrl: String? = null,
    val productLink: String? = null,
    val offerLink: String? = null
)
```

## Tratamento de Shortlinks

O sistema resolve automaticamente shortlinks do formato `https://s.shopee.com.br/xxxxx` seguindo os redirects para obter a URL completa com `shopId` e `itemId`.

## Logs de Debug

O sistema registra os seguintes logs para debug:
- Timestamp usado
- Payload enviado
- Signature gerada
- Resposta da API

## Referências
- Documentação oficial da Shopee Open API
- GraphQL endpoint: https://open-api.affiliate.shopee.com.br/graphql
