package org.iutools.script

/*
 * Only the handful of methods actually reachable from decomposeWord() were
 * ported at first: isConsonant/typeOfLetterLat (stem-context
 * classification), nasalOfOcclusiveUnvoicedLat/voicedOfOcclusiveUnvoicedLat
 * (consonant mutation lookups). The syllabics-conversion tables and other
 * helpers in the original 1400-line Roman.java were unused by the R2L
 * engine and left out.
 *
 * `allInuktitut` and `transcodeToSyllabics` (ported from the original's
 * `transcodeToUnicode`) were added later, for the app's user-facing
 * display-script setting (Roman/Syllabic/as-entered) -- they're used by
 * TransCoder.kt, not by decomposeWord() itself. `transcodeToSyllabics`
 * drops the original's `aipaitaiMode` parameter: nothing reachable from
 * this app ever requests that legacy syllabic character variant, so every
 * `aipaitai` branch below always takes its `default` case.
 */
object Roman {
    const val V = 0 // verbe; voyelle
    const val C = 2 // consonne

    private const val inuktitutCharacters = "bgHjklmnpqrstv&aiu"

    private val consonants = charArrayOf(
        'g', 'h', 'j', 'k', 'l', 'm', 'n', 'p', 'q', 'r', 's', 't', 'v', '&', 'N', 'X', 'H'
    ).sortedArray()
    private val vowels = charArrayOf('a', 'i', 'u').sortedArray()

    @JvmStatic
    fun typeOfLetterLat(letter: Char): Int {
        return if (consonants.binarySearch(letter) >= 0) C
        else if (vowels.binarySearch(letter) >= 0) V
        else -1
    }

    @JvmStatic
    fun isConsonant(charac: Char): Boolean = typeOfLetterLat(charac) == C

    @JvmStatic
    fun nasalOfOcclusiveUnvoicedLat(n: Char): Char {
        return when (n) {
            'p' -> 'm'
            't' -> 'n'
            'k' -> 'N'
            'q' -> 'r'
            else -> (-1).toChar()
        }
    }

    @JvmStatic
    fun voicedOfOcclusiveUnvoicedLat(n: Char): Char {
        return when (n) {
            'p' -> 'v'
            't' -> 'l'
            'k' -> 'g'
            'q' -> 'r'
            else -> (-1).toChar()
        }
    }

    @JvmStatic
    fun allInuktitut(word: String): Boolean {
        val lower = word.lowercase()
        for (c in lower) {
            if (inuktitutCharacters.indexOf(c) == -1) return false
        }
        return true
    }

    private fun prepareString(str: String): String {
        var s = str.replace("nng", "X")
        s = s.replace("ng", "N")
        s = s.replace("qq", "Q")
        s = s.replace("rq", "Q")
        return s
    }

    @JvmStatic
    fun transcodeToSyllabics(str: String): String {
        val s = prepareString(str)
        // aipaitaiMode dropped -- always the non-aipaitai branch (see class header)
        val aipaitai = 0
        var i = 0
        val l = s.length
        var c: Char
        var d = ' '
        var e = ' '
        val sb = StringBuilder()
        while (i < l) {
            c = s[i];
            when (c) {
            'i' -> {
                i++;
                if (i < l) {
                    e = s[i];
                    when (e) {
                    'i' -> { d = '\u1404'; } // ii
                    else -> { d = '\u1403'; i--; }
                    }
                } else {
                    d = '\u1403';
                    i--;
                }
            }
            'u' -> {
                i++;
                if (i < l) {
                    e = s[i];
                    when (e) {
                    'u' -> { d = '\u1406'; } // uu
                    else -> { d = '\u1405'; i--; }
                    }
                } else {
                    d = '\u1405';
                    i--;
                }
            }
            'a' -> { // a
                i++;
                if (i < l) {
                    e = s[i];
                    when (e) {
                    'a' -> { d = '\u140b'; } // aa
                    'i' -> {
                        when (aipaitai) {
                        1 -> { d = '\u1401'; } // ai
                        else -> { d = '\u140A'; i--; }
                        }
                    }
                    else -> { d = '\u140A'; i--; }
                    }
                } else {
                    d = '\u140A';
                    i--;
                }
            }
            'b', 'p' -> {
                i++;
                if (i < l) {
                    e = s[i];
                    when (e) {
                    'i' -> {
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'i' -> { d = '\u1432'; } // pii
                            else -> { d = '\u1431'; i--; } // pi
                            }
                        } else {
                            d = '\u1431';
                            i--;
                        }
                    }
                    'u' -> {
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'u' -> { d = '\u1434'; } // puu
                            else -> { d = '\u1433'; i--; } // pu
                            }
                        } else {
                            d = '\u1433';
                            i--;
                        }
                    }
                    'a' -> { // pa
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'a' -> { d = '\u1439'; } // paa
                            'i' -> {
                                when (aipaitai) {
                                1 -> { d = '\u142f'; } // pai
                                else -> { d = '\u1438'; i--; } // pa
                                }
                            }
                            else -> { d = '\u1438'; i--; } // pa
                            }
                        } else {
                            d = '\u1438'; // pa
                            i--;
                        }
                    }
                    else -> { d = '\u1449'; i--; } // p
                    }
                } else {
                    i--;
                    d = '\u1449'; // p
                }
            }
            'd', 't' -> {
                i++;
                if (i < l) {
                    e = s[i];
                    when (e) {
                    'i' -> {
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'i' -> { d = '\u144f'; } // tii
                            else -> { d = '\u144e'; i--; } // ti
                            }
                        } else {
                            d = '\u144e';
                            i--;
                        }
                    }
                    'u' -> {
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'u' -> { d = '\u1451'; } // tuu
                            else -> { d = '\u1450'; i--; } // tu
                            }
                        } else {
                            d = '\u1450';
                            i--;
                        }
                    }
                    'a' -> { // ta
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'a' -> { d = '\u1456'; } // taa
                            'i' -> {
                                when (aipaitai) {
                                1 -> { d = '\u144c'; } // tai
                                else -> { d = '\u1455'; i--; } // ta
                                }
                            }
                            else -> { d = '\u1455'; i--; } // ta
                            }
                        } else {
                            d = '\u1455'; // ta
                            i--;
                        }
                    }
                    else -> { d = '\u1466'; i--; } // t
                    }
                } else {
                    i--;
                    d = '\u1466'; // t
                }
            }
            'k' -> {
                i++;
                if (i < l) {
                    e = s[i];
                    when (e) {
                    'i' -> {
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'i' -> { d = '\u146e'; } // kii
                            else -> { d = '\u146d'; i--; } // ki
                            }
                        } else {
                            d = '\u146d';
                            i--;
                        }
                    }
                    'u' -> {
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'u' -> { d = '\u1470'; } // kuu
                            else -> { d = '\u146f'; i--; } // ku
                            }
                        } else {
                            d = '\u146f';
                            i--;
                        }
                    }
                    'a' -> { // 
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'a' -> { d = '\u1473'; } // kaa
                            'i' -> {
                                when (aipaitai) {
                                1 -> { d = '\u146b'; } // kai
                                else -> { d = '\u1472'; i--; } // ka
                                }
                            }
                            else -> { d = '\u1472'; i--; } // ka
                            }
                        } else {
                            d = '\u1472'; // ka
                            i--;
                        }
                    }
                    else -> { d = '\u1483'; i--; } // k
                    }
                } else {
                    i--;
                    d = '\u1483'; // k
                }
            }
            'g' -> {
                i++;
                if (i < l) {
                    e = s[i];
                    when (e) {
                    'i' -> {
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'i' -> { d = '\u148c'; } // gii
                            else -> { d = '\u148b'; i--; } // gi
                            }
                        } else {
                            d = '\u148b';
                            i--;
                        }
                    }
                    'u' -> {
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'u' -> { d = '\u148e'; } // guu
                            else -> { d = '\u148d'; i--; } // gu
                            }
                        } else {
                            d = '\u148d';
                            i--;
                        }
                    }
                    'a' -> { // 
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'a' -> { d = '\u1491'; } // gaa
                            'i' -> {
                                when (aipaitai) {
                                1 -> { d = '\u1489'; } // gai
                                else -> { d = '\u1490'; i--; } // ga
                                }
                            }
                            else -> { d = '\u1490'; i--; } // ga
                            }
                        } else {
                            d = '\u1490'; // ga
                            i--;
                        }
                    }
                    else -> { d = '\u14a1'; i--; } // g
                    }
                } else {
                    i--;
                    d = '\u14a1'; // g
                }
            }
            'm' -> {
                i++;
                if (i < l) {
                    e = s[i];
                    when (e) {
                    'i' -> {
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'i' -> { d = '\u14a6'; } // mii
                            else -> { d = '\u14a5'; i--; } // mi
                            }
                        } else {
                            d = '\u14a5';
                            i--;
                        }
                    }
                    'u' -> {
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'u' -> { d = '\u14a8'; } // muu
                            else -> { d = '\u14a7'; i--; } // mu
                            }
                        } else {
                            d = '\u14a7';
                            i--;
                        }
                    }
                    'a' -> { // 
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'a' -> { d = '\u14ab'; } //maa
                            'i' -> {
                                when (aipaitai) {
                                1 -> { d = '\u14a3'; } // mai
                                else -> { d = '\u14aa'; i--; } // ma
                                }
                            }
                            else -> { d = '\u14aa'; i--; } // ma
                            }
                        } else {
                            d = '\u14aa'; // ma
                            i--;
                        }
                    }
                    else -> { d = '\u14bb'; i--; } // m
                    }
                } else {
                    i--;
                    d = '\u14bb'; // m
                }
            }
           'n' -> {
               i++;
               if (i < l) {
                   e = s[i];
                   when (e) {
                   'i' -> {
                       i++;
                       if (i < l) {
                           e = s[i];
                           when (e) {
                           'i' -> { d = '\u14c3'; } // nii
                           else -> { d = '\u14c2'; i--; } // ni
                           }
                       } else {
                           d = '\u14c2';
                           i--;
                       }
                   }
                   'u' -> {
                       i++;
                       if (i < l) {
                           e = s[i];
                           when (e) {
                           'u' -> { d = '\u14c5'; } // nuu
                           else -> { d = '\u14c4'; i--; } // nu
                           }
                       } else {
                           d = '\u14c4';
                           i--;
                       }
                   }
                   'a' -> { // 
                       i++;
                       if (i < l) {
                           e = s[i];
                           when (e) {
                           'a' -> { d = '\u14c8'; } //naa
                           'i' -> {
                               when (aipaitai) {
                               1 -> { d = '\u14c0'; } // nai
                               else -> { d = '\u14c7'; i--; } // na
                               }
                           }
                           else -> { d = '\u14c7'; i--; } // na
                           }
                       } else {
                           d = '\u14c7'; // na
                           i--;
                       }
                   }
                   else -> { d = '\u14d0'; i--; } // n
                   }
               } else {
                   i--;
                   d = '\u14d0'; // n
               }
           }
           'h', 's' -> {
               i++;
               if (i < l) {
                   e = s[i];
                   when (e) {
                   'i' -> {
                       i++;
                       if (i < l) {
                           e = s[i];
                           when (e) {
                           'i' -> { d = '\u14f0'; } // sii
                           else -> { d = '\u14ef'; i--; } // si
                           }
                       } else {
                           d = '\u14ef';
                           i--;
                       }
                   }
                   'u' -> {
                       i++;
                       if (i < l) {
                           e = s[i];
                           when (e) {
                           'u' -> { d = '\u14f2'; } // suu
                           else -> { d = '\u14f1'; i--; } // su
                           }
                       } else {
                           d = '\u14f1';
                           i--;
                       }
                   }
                   'a' -> { // 
                       i++;
                       if (i < l) {
                           e = s[i];
                           when (e) {
                           'a' -> { d = '\u14f5'; } //saa
                           'i' -> {
                               when (aipaitai) {
                               1 -> { d = '\u14ed'; } // sai
                               else -> { d = '\u14f4'; i--; } // sa
                               }
                           }
                           else -> { d = '\u14f4'; i--; } // sa
                           }
                       } else {
                           d = '\u14f4'; // sa
                           i--;
                       }
                   }
                   else -> { d = '\u1505'; i--; } // s
                   }
               } else {
                   i--;
                   d = '\u1505'; // s
               }
           }
            'l' -> {
                i++;
                if (i < l) {
                    e = s[i];
                    when (e) {
                    'i' -> {
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'i' -> { d = '\u14d6'; } // lii
                            else -> { d = '\u14d5'; i--; } // li
                            }
                        } else {
                            d = '\u14d5';
                            i--;
                        }
                    }
                    'u' -> {
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'u' -> { d = '\u14d8'; } // luu
                            else -> { d = '\u14d7'; i--; } // lu
                            }
                        } else {
                            d = '\u14d7';
                            i--;
                        }
                    }
                    'a' -> { // 
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'a' -> { d = '\u14db'; } //laa
                            'i' -> {
                                when (aipaitai) {
                                1 -> { d = '\u14d3'; } // lai
                                else -> { d = '\u14da'; i--; } // la
                                }
                            }
                            else -> { d = '\u14da'; i--; } // la
                            }
                        } else {
                            d = '\u14da'; // la
                            i--;
                        }
                    }
                    else -> { d = '\u14ea'; i--; } // l
                    }
                } else {
                    i--;
                    d = '\u14ea'; // l
                }
            }
            'j' -> {
                i++;
                if (i < l) {
                    e = s[i];
                    when (e) {
                    'i' -> {
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'i' -> { d = '\u1529'; } // jii
                            else -> { d = '\u1528'; i--; } // ji
                            }
                        } else {
                            d = '\u1528';
                            i--;
                        }
                    }
                    'u' -> {
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'u' -> { d = '\u152b'; } // juu
                            else -> { d = '\u152a'; i--; } // ju
                            }
                        } else {
                            d = '\u152a';
                            i--;
                        }
                    }
                    'a' -> { // 
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'a' -> { d = '\u152e'; } //jaa
                            'i' -> {
                                when (aipaitai) {
                                1 -> { d = '\u1526'; } // jai
                                else -> { d = '\u152d'; i--; } // ja
                                }
                            }
                            else -> { d = '\u152d'; i--; } // ja
                            }
                        } else {
                            d = '\u152d'; // ja
                            i--;
                        }
                    }
                    else -> { d = '\u153e'; i--; } // j
                    }
                } else {
                    i--;
                    d = '\u153e'; // j
                }
            }
            'v' -> {
                i++;
                if (i < l) {
                    e = s[i];
                    when (e) {
                    'i' -> {
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'i' -> { d = '\u1556'; } // vii
                            else -> { d = '\u1555'; i--; } // vi
                            }
                        } else {
                            d = '\u1555';
                            i--;
                        }
                    }
                    'u' -> {
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'u' -> { d = '\u1558'; } // vuu
                            else -> { d = '\u1557'; i--; } // vu
                            }
                        } else {
                            d = '\u1557';
                            i--;
                        }
                    }
                    'a' -> { // 
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'a' -> { d = '\u155a'; } //vaa
                            'i' -> {
                                when (aipaitai) {
                                1 -> { d = '\u1553'; } // vai
                                else -> { d = '\u1559'; i--; } // va
                                }
                            }
                            else -> { d = '\u1559'; i--; } // va
                            }
                        } else {
                            d = '\u1559'; // va
                            i--;
                        }
                    }
                    else -> { d = '\u155d'; i--; } // v
                    }
                } else {
                    i--;
                    d = '\u155d'; // v
                }
            }
            'r' -> {
                i++;
                if (i < l) {
                    e = s[i];
                    when (e) {
                    'i' -> {
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'i' -> { d = '\u1547'; } // rii
                            else -> { d = '\u1546'; i--; } // ri
                            }
                        } else {
                            d = '\u1546';
                            i--;
                        }
                    }
                    'u' -> {
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'u' -> { d = '\u1549'; } // ruu
                            else -> { d = '\u1548'; i--; } // ru
                            }
                        } else {
                            d = '\u1548';
                            i--;
                        }
                    }
                    'a' -> { // 
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'a' -> { d = '\u154c'; } //raa
                            'i' -> {
                                when (aipaitai) {
                                1 -> { d = '\u1542'; } // rai
                                else -> { d = '\u154b'; i--; } // ra
                                }
                            }
                            else -> { d = '\u154b'; i--; } // ra
                            }
                        } else {
                            d = '\u154b'; // ra
                            i--;
                        }
                    }
                    else -> { d = '\u1550'; i--; } // r
                    }
                } else {
                    i--;
                    d = '\u1550'; // r
                }
            }
            'q' -> {
                i++;
                if (i < l) {
                    e = s[i];
                    when (e) {
                    'i' -> {
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'i' -> { d = '\u1580'; } // qii
                            else -> { d = '\u157f'; i--; } // qi
                            }
                        } else {
                            d = '\u157f';
                            i--;
                        }
                    }
                    'u' -> {
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'u' -> { d = '\u1582'; } // quu
                            else -> { d = '\u1581'; i--; } // qu
                            }
                        } else {
                            d = '\u1581';
                            i--;
                        }
                    }
                    'a' -> { // 
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'a' -> { d = '\u1584'; } //qaa
                            'i' -> {
                                when (aipaitai) {
                                1 -> { d = '\u166f'; } // qai
                                else -> { d = '\u1583'; i--; } // qa
                                }
                            }
                            else -> { d = '\u1583'; i--; } // qa
                            }
                        } else {
                            d = '\u1583'; // qa
                            i--;
                        }
                    }
                    else -> { d = '\u1585'; i--; } // q
                    }
                } else {
                    i--;
                    d = '\u1585'; // q
                }
            }
            '&' -> {
                i++;
                if (i < l) {
                    e = s[i];
                    when (e) {
                    'i' -> {
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'i' -> { d = '\u15a1'; } // &ii
                            else -> { d = '\u15a0'; i--; } // &i
                            }
                        } else {
                            d = '\u15a0';
                            i--;
                        }
                    }
                    'u' -> {
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'u' -> { d = '\u15a3'; } // &uu
                            else -> { d = '\u15a2'; i--; } // &u
                            }
                        } else {
                            d = '\u15a2';
                            i--;
                        }
                    }
                    'a' -> { // 
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'a' -> { d = '\u15a5'; } //&aa
                            'i' -> {
                                when (aipaitai) {
                                1 -> { sb.append('\u15a4'); d = '\u1403'; } // &ai
                                else -> { d = '\u15a4'; i--; } // &a
                                }
                            }
                            else -> { d = '\u15a4'; i--; } // &a
                            }
                        } else {
                            d = '\u15a4'; // &a
                            i--;
                        }
                    }
                    else -> { d = '\u15a6'; i--; } // &
                    }
                } else {
                    i--;
                    d = '\u15a6'; // &
                }
            }
            'N' -> {
                i++;
                if (i < l) {
                    e = s[i];
                    when (e) {
                    'i' -> {
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'i' -> { d = '\u1590'; } // ngii
                            else -> { d = '\u158f'; i--; } // ngi
                            }
                        } else {
                            d = '\u158f';
                            i--;
                        }
                    }
                    'u' -> {
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'u' -> { d = '\u1592'; } // nguu
                            else -> { d = '\u1591'; i--; } // ngu
                            }
                        } else {
                            d = '\u1591';
                            i--;
                        }
                    }
                    'a' -> { // 
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'a' -> { d = '\u1594'; } //ngaa
                            'i' -> {
                                when (aipaitai) {
                                1 -> { d = '\u1670'; } // ngai
                                else -> { d = '\u1593'; i--; } // nga
                                }
                            }
                            else -> { d = '\u1593'; i--; } // nga
                            }
                        } else {
                            d = '\u1593'; // nga
                            i--;
                        }
                    }
                    else -> { d = '\u1595'; i--; } // ng
                    }
                } else {
                    i--;
                    d = '\u1595'; // ng
                }
            }
            'X' -> {
                i++;
                if (i < l) {
                    e = s[i];
                    when (e) {
                    'i' -> {
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'i' -> { d = '\u1672'; } // nngii
                            else -> { d = '\u1671'; i--; } // nngi
                            }
                        } else {
                            d = '\u1671';
                            i--;
                        }
                    }
                    'u' -> {
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'u' -> { d = '\u1674'; } // nnguu
                            else -> { d = '\u1673'; i--; } // nngu
                            }
                        } else {
                            d = '\u1673';
                            i--;
                        }
                    }
                    'a' -> { // 
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'a' -> { d = '\u1676'; } //nngaa
                            'i' -> {
                                when (aipaitai) {
                                1 -> { sb.append('\u1596'); d = '\u1489'; } // nngai
                                else -> { d = '\u1675'; i--; } // nnga
                                }
                            }
                            else -> { d = '\u1675'; i--; } // nnga
                            }
                        } else {
                            d = '\u1675'; // nnga
                            i--;
                        }
                    }
                    else -> { d = '\u1596'; i--; } // nng
                    }
                } else {
                    i--;
                    d = '\u1596'; // nng
                }
            }
//            case 'h': d = '\u157C'; break; // Nunavut H
//            case 'b': d = '\u15AF'; break; // Aivilik B
                // Nunavik H
            'Q' -> { //qq
                sb.append('\u1585');
                i++;
                if (i < l) {
                    e = s[i];
                    when (e) {
                    'i' -> {
                        d = '\u146d';
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'i' -> { d++; } // qqii = q+kii
                            else -> { i--; } // qqi = q+ki
                            }
                        } else {
                            i--;
                        }
                    }
                    'u' -> {
                        d = '\u146f';
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'u' -> { d++; } // qquu = q+kuu
                            else -> { i--; } // qqu = q+ku
                            }
                        } else {
                            i--;
                        }
                    }
                    'a' -> {
                        d = '\u1472';
                        i++;
                        if (i < l) {
                            e = s[i];
                            when (e) {
                            'a' -> { d++; } // qqaa = q+kaa
                            'i' -> {
                                when (aipaitai) {
                                1 -> { d = '\u146b'; } // qqai = q+kai
                                else -> { i--; } // qqa = q+ka
                                }
                            }
                            else -> { i--; } // qqa = q+ka
                            }
                        } else {
                            // qqa = q+ka
                            i--;
                        }
                    }
                    else -> { // shouldn't happen 
                        d = '\u1585'; i--;
                    } // qq
                    }
                } else { // shouldn't happen
                    i--;
                    d = '\u1585';
                    sb.deleteCharAt(sb.length-1);
                }
            }
            'H' -> { d = '\u157c'; }
            else -> { d = c; }
            }
            i++;
            sb.append(d);
        }
        return sb.toString()
    }
}
