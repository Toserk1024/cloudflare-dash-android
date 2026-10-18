package io.github.toserk1024.cfdash.data.storage

import io.github.toserk1024.cfdash.data.api.AuthCredential
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 账号 id 生成测试。
 * 重点保护“算法不得变更”：id 变了会导致已保存的加密存储 key 失配，等同于账号丢失。
 */
class AccountIdTest {

    @Test
    fun tokenIdIsPrefixedAndDerivedFromContentHash() {
        val token = AuthCredential.Token("sample-token-value")
        assertEquals("tok_${"sample-token-value".hashCode()}", AccountId.of(token))
    }

    @Test
    fun globalKeyIdUsesLowercaseEmail() {
        val credential = AuthCredential.GlobalKey("User@Example.COM", "api-key")
        assertEquals("eml_user@example.com", AccountId.of(credential))
    }

    @Test
    fun differentTokensProduceDifferentIds() {
        assertNotEquals(
            AccountId.of(AuthCredential.Token("token-a")),
            AccountId.of(AuthCredential.Token("token-b"))
        )
    }

    @Test
    fun labelsDifferByCredentialType() {
        assertEquals("Token 用户", AccountId.labelOf(AuthCredential.Token("x")))
        assertEquals("user@example.com", AccountId.labelOf(AuthCredential.GlobalKey("user@example.com", "k")))
    }
}