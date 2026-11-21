package cz.multio.doklady

/**
 * Pevné nastavení aplikace. Klient nic nezadává – aplikace míří na server účetní kanceláře
 * a podle přihlášeného účtu ukáže jeho klientský portál (jeho firmu).
 */
object AppConfig {
    /** Adresa serveru s klientskými portály (stejná jako PUBLIC_URL na serveru). */
    const val SERVER_URL = "https://doklady.ucetnictvitabor.cz"

    /** Odkazy z pozvánek: multiodoklady://activate?code=ABCD-EFGH (stejné jako APP_SCHEME na serveru). */
    const val APP_SCHEME = "multiodoklady"
}
