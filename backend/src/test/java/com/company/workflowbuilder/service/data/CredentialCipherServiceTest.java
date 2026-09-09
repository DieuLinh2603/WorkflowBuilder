package com.company.workflowbuilder.service.data;
import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.*;
class CredentialCipherServiceTest {@Test void encryptsWithRandomNonceAndDecrypts(){CredentialCipherService c=new CredentialCipherService("0123456789abcdef-test-secret");String a=c.encrypt("{\"token\":\"secret\"}"),b=c.encrypt("{\"token\":\"secret\"}");assertThat(a).doesNotContain("secret").isNotEqualTo(b);assertThat(c.decrypt(a)).isEqualTo("{\"token\":\"secret\"}");}}
