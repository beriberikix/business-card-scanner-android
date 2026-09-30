package dev.jberi.cardscan.parse

import dev.jberi.cardscan.model.ContactCard
import dev.jberi.cardscan.ocr.OcrResult

/**
 * Turns OCR output into contact fields.
 *
 * Kept as an interface because the parsing backend is the part most likely to change: an
 * on-device LLM could replace the heuristics without capture, OCR or saving noticing. On this
 * device the ML Kit GenAI Prompt API reports UNAVAILABLE (see NanoSpike), so the heuristic
 * implementation is the one in use.
 *
 * @param defaultRegion ISO 3166-1 alpha-2, used to resolve phone numbers written without a
 *   country code.
 */
interface CardParser {
  fun parse(ocr: OcrResult, defaultRegion: String = "US"): ContactCard
}
