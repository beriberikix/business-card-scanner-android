package dev.jberi.cardscan.parse

/** Word lists and patterns used to classify card lines. Deliberately plain data. */
internal object Lexicons {

  /**
   * Legal-form markers. A line containing one is almost certainly the company, because these
   * words appear in essentially no job title.
   */
  val COMPANY_STRONG = setOf(
    "inc", "inc.", "llc", "l.l.c.", "ltd", "ltd.", "limited", "corp", "corp.", "corporation",
    "co.", "company", "gmbh", "mbh", "ag", "kg", "ohg", "ug", "bv", "b.v.", "nv", "n.v.",
    "s.a.", "sas", "sarl", "s.r.l.", "srl", "spa", "s.p.a.", "a/s", "oy", "oyj",
    "aps", "plc", "llp", "pty", "pte", "k.k.", "pllc", "holdings", "holding",
  )

  /**
   * Industry words that often appear in a company name but also show up in job titles
   * ("Senior Solutions Engineer"). Only treated as company evidence on a line that is not
   * already a job title.
   */
  val COMPANY_WEAK = setOf(
    "group", "partners", "ventures", "labs", "studio", "studios", "agency", "associates",
    "consulting", "solutions", "systems", "technologies", "technology", "industries",
    "enterprises", "international", "worldwide", "global", "foundation", "institute",
    "university", "college", "media", "capital", "digital", "networks", "software",
  )

  /** Job-title markers. Matched against whole words in a line. */
  val TITLE_MARKERS = setOf(
    "ceo", "cto", "cfo", "coo", "cio", "cmo", "cpo", "cso", "chief", "president", "vp",
    "svp", "evp", "avp", "vice", "director", "dir", "manager", "mgr", "head", "lead", "leader",
    "principal", "senior", "junior", "staff", "founder", "cofounder", "co-founder", "owner",
    "partner", "associate", "assistant", "engineer", "engineering", "developer", "programmer",
    "architect", "designer", "scientist", "researcher", "analyst", "consultant", "specialist",
    "coordinator", "administrator", "administrative", "supervisor", "officer", "executive",
    "representative", "rep", "advisor", "adviser", "strategist", "producer", "editor", "writer",
    "accountant", "controller", "attorney", "lawyer", "counsel", "paralegal", "agent", "broker",
    "realtor", "nurse", "doctor", "physician", "therapist", "dentist", "professor", "lecturer",
    "teacher", "instructor", "trainer", "technician", "operations", "sales", "marketing",
    "recruiter", "buyer", "planner", "auditor", "surveyor", "inspector", "foreman",
    // Common non-English titles, since cards are often not in English.
    "leiter", "leiterin", "technische", "technischer", "geschäftsführer", "geschaeftsfuehrer",
    "inhaber", "direktor", "vorstand", "prokurist", "berater", "beraterin", "entwickler",
    "ingenieur", "vertrieb", "einkauf", "directeur", "directrice", "responsable", "gérant",
    "ingénieur", "chargé", "ingeniero", "gerente", "jefe", "socio", "titular",
  )

  /** Honorifics that precede a name. */
  val NAME_PREFIXES = setOf(
    "mr", "mr.", "mrs", "mrs.", "ms", "ms.", "miss", "dr", "dr.", "prof", "prof.", "rev", "rev.",
    "sir", "dame", "hon", "hon.", "capt", "capt.", "col", "col.", "sgt", "sgt.", "lt", "lt.",
    "herr", "frau", "mme", "mlle", "sr", "sra", "ing", "ing.", "dipl.-ing",
  )

  /** Post-nominals that follow a name and should not be treated as a surname. */
  val NAME_SUFFIXES = setOf(
    "jr", "jr.", "sr", "sr.", "ii", "iii", "iv", "v", "phd", "ph.d.", "md", "m.d.", "dds", "dvm",
    "esq", "esq.", "cpa", "mba", "msc", "ma", "ba", "bsc", "bs", "rn", "pe", "pmp", "cfa", "cissp",
    "cfp", "do", "jd", "llm", "edd", "psyd",
  )

  /** Street/address markers. */
  val ADDRESS_MARKERS = setOf(
    "street", "st", "st.", "road", "rd", "rd.", "avenue", "ave", "ave.", "boulevard", "blvd",
    "blvd.", "lane", "ln", "ln.", "drive", "dr", "dr.", "court", "ct", "ct.", "place", "pl",
    "pl.", "square", "sq", "sq.", "terrace", "parkway", "pkwy", "highway", "hwy", "route", "rte",
    "suite", "ste", "ste.", "apt", "apartment", "unit", "floor", "fl", "flr", "building", "bldg",
    "po", "p.o.", "box", "wharf", "quay", "close", "crescent", "way", "walk", "gardens", "park",
    "strasse", "straße", "str", "str.", "platz", "weg", "allee", "gasse", "rue", "boulevard",
    "avenida", "calle", "via", "piazza", "viale", "corso",
  )

  val COUNTRIES = setOf(
    "usa", "u.s.a.", "us", "united states", "canada", "uk", "u.k.", "united kingdom", "england",
    "scotland", "wales", "ireland", "germany", "deutschland", "france", "spain", "españa",
    "italy", "italia", "netherlands", "nederland", "belgium", "switzerland", "schweiz",
    "austria", "österreich", "sweden", "norway", "denmark", "finland", "poland", "portugal",
    "japan", "china", "india", "singapore", "australia", "new zealand", "brazil", "mexico",
    "south africa", "israel", "uae", "united arab emirates",
  )

  /** Postal-code shapes: US ZIP, UK, Canada, and generic 4-6 digit European codes. */
  val POSTAL_PATTERNS = listOf(
    Regex("""\b\d{5}(-\d{4})?\b"""),
    Regex("""\b[A-Z]{1,2}\d[A-Z\d]?\s*\d[A-Z]{2}\b""", RegexOption.IGNORE_CASE),
    Regex("""\b[A-Z]\d[A-Z]\s*\d[A-Z]\d\b""", RegexOption.IGNORE_CASE),
    Regex("""\b\d{4,6}\b"""),
  )

  val EMAIL = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")

  /** URLs, including bare domains and common social handles written as paths. */
  val URL = Regex(
    """\b((https?://|www\.)[^\s,;]+|[A-Za-z0-9\-]+\.(com|net|org|io|co|dev|ai|app|biz|info|me|us|uk|de|fr|es|it|nl|be|ch|at|se|no|dk|fi|pl|pt|jp|cn|in|sg|au|nz|br|mx|za|il|ae|eu|tech|design|agency|studio|group|cloud|digital|online|store|shop)(\.[a-z]{2})?(/[^\s,;]*)?)\b""",
    RegexOption.IGNORE_CASE,
  )

  /** Noise lines that carry no contact data. */
  val NOISE = Regex(
    """^\s*(follow us|connect with us|scan me|scan here|www\.?|email|e-mail|tel|phone|fax|mobile|address|web|website)\s*[:.]?\s*$""",
    RegexOption.IGNORE_CASE,
  )
}
