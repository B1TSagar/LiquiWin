package io.github.b1tsagar.liquiwin.core.testing

/** The smallest moving picture: a 1 × 1 GIF that turns orange to white and back every 0.5 s, for ever. */
fun movingGif(): ByteArray = (
    "474946383961" + "01000100800000" + "F09609FFFFFF" + // the header, a 1 × 1 screen, and its two colours
        "21FF0B4E45545343415045322E300301000000" + // loop for ever
        "21F9040032000000" + "2C000000000100010000" + "0202440100" + // 0.5 s of orange
        "21F9040032000000" + "2C000000000100010000" + "02024C0100" + // 0.5 s of white
        "3B"
    ).hexToByteArray()
