package com.starclan.starhub.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiUrlCheckTest {
    @Test fun https_ok() = assertEquals(ApiUrlCheck.Result.OK, ApiUrlCheck.check("https://owo-tracker.up.railway.app"))
    @Test fun https_uppercase_ok() = assertEquals(ApiUrlCheck.Result.OK, ApiUrlCheck.check("HTTPS://example.com"))
    @Test fun trims_spaces() = assertEquals(ApiUrlCheck.Result.OK, ApiUrlCheck.check("  https://example.com  "))
    @Test fun http_blocked() = assertEquals(ApiUrlCheck.Result.CLEARTEXT_BLOCKED, ApiUrlCheck.check("http://192.168.1.5:8080"))
    @Test fun no_scheme() = assertEquals(ApiUrlCheck.Result.MISSING_SCHEME, ApiUrlCheck.check("owo-tracker.up.railway.app"))
    @Test fun empty() = assertEquals(ApiUrlCheck.Result.EMPTY, ApiUrlCheck.check("   "))
}
