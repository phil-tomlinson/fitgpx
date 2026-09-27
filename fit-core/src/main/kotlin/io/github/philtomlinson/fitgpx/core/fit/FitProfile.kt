/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.core.fit

/**
 * The small subset of the FIT global profile that FitGPX needs: message numbers, field numbers,
 * scales and enum names. Values are facts from the public FIT protocol description.
 */
object FitProfile {
    /** Seconds between the Unix epoch and the FIT epoch (1989-12-31T00:00:00Z). */
    const val FIT_EPOCH_OFFSET_S = 631_065_600L

    /** Timestamps below this value are relative (seconds since device power-on), not absolute. */
    const val MIN_ABSOLUTE_TIMESTAMP = 0x10000000L

    const val FIELD_TIMESTAMP = 253
    const val FIELD_MESSAGE_INDEX = 254

    /** Semicircles to degrees: 180 / 2^31. */
    const val SEMICIRCLE_TO_DEG = 180.0 / 2147483648.0

    object Mesg {
        const val FILE_ID = 0
        const val SPORT = 12
        const val SESSION = 18
        const val LAP = 19
        const val RECORD = 20
        const val EVENT = 21
        const val DEVICE_INFO = 23
        const val COURSE = 31
        const val COURSE_POINT = 32
        const val ACTIVITY = 34
        const val SEGMENT_POINT = 150
    }

    object FileId {
        const val TYPE = 0
        const val MANUFACTURER = 1
        const val PRODUCT = 2
        const val SERIAL_NUMBER = 3
        const val TIME_CREATED = 4
        const val PRODUCT_NAME = 8
    }

    object Record {
        const val POSITION_LAT = 0
        const val POSITION_LONG = 1
        const val ALTITUDE = 2 // uint16, scale 5, offset 500 (m)
        const val HEART_RATE = 3 // uint8 bpm
        const val CADENCE = 4 // uint8 rpm
        const val DISTANCE = 5 // uint32, scale 100 (m)
        const val SPEED = 6 // uint16, scale 1000 (m/s)
        const val POWER = 7 // uint16 W
        const val TEMPERATURE = 13 // sint8 C
        const val ENHANCED_SPEED = 73 // uint32, scale 1000 (m/s)
        const val ENHANCED_ALTITUDE = 78 // uint32, scale 5, offset 500 (m)
    }

    object Session {
        const val START_TIME = 2
        const val SPORT = 5
        const val SUB_SPORT = 6
        const val TOTAL_ELAPSED_TIME = 7 // scale 1000 (s)
        const val TOTAL_TIMER_TIME = 8 // scale 1000 (s)
        const val TOTAL_DISTANCE = 9 // scale 100 (m)
        const val TOTAL_ASCENT = 22 // m
        const val SPORT_PROFILE_NAME = 110
    }

    object Lap {
        const val START_TIME = 2
        const val START_POSITION_LAT = 3
        const val START_POSITION_LONG = 4
        const val END_POSITION_LAT = 5
        const val END_POSITION_LONG = 6
        const val TOTAL_ELAPSED_TIME = 7
        const val TOTAL_DISTANCE = 9
    }

    object Event {
        const val EVENT = 0
        const val EVENT_TYPE = 1

        const val EVENT_TIMER = 0
        const val TYPE_START = 0
        const val TYPE_STOP = 1
        const val TYPE_STOP_ALL = 4
        const val TYPE_STOP_DISABLE = 8
        const val TYPE_STOP_DISABLE_ALL = 9
    }

    object Sport {
        const val SPORT = 0
        const val SUB_SPORT = 1
        const val NAME = 3
    }

    object Course {
        const val SPORT = 4
        const val NAME = 5
    }

    object CoursePoint {
        const val TIMESTAMP = 1
        const val POSITION_LAT = 2
        const val POSITION_LONG = 3
        const val DISTANCE = 4
        const val TYPE = 5
        const val NAME = 6
    }

    object Activity {
        const val LOCAL_TIMESTAMP = 5
    }

    object FileType {
        const val ACTIVITY = 4
        const val COURSE = 6
    }

    /** Sport enum → snake_case name. */
    val SPORTS: Map<Int, String> = mapOf(
        0 to "generic", 1 to "running", 2 to "cycling", 3 to "transition", 4 to "fitness_equipment",
        5 to "swimming", 6 to "basketball", 7 to "soccer", 8 to "tennis", 9 to "american_football",
        10 to "training", 11 to "walking", 12 to "cross_country_skiing", 13 to "alpine_skiing",
        14 to "snowboarding", 15 to "rowing", 16 to "mountaineering", 17 to "hiking", 18 to "multisport",
        19 to "paddling", 20 to "flying", 21 to "e_biking", 22 to "motorcycling", 23 to "boating",
        24 to "driving", 25 to "golf", 26 to "hang_gliding", 27 to "horseback_riding", 28 to "hunting",
        29 to "fishing", 30 to "inline_skating", 31 to "rock_climbing", 32 to "sailing", 33 to "ice_skating",
        34 to "sky_diving", 35 to "snowshoeing", 36 to "snowmobiling", 37 to "stand_up_paddleboarding",
        38 to "surfing", 39 to "wakeboarding", 40 to "water_skiing", 41 to "kayaking", 42 to "rafting",
        43 to "windsurfing", 44 to "kitesurfing", 45 to "tactical", 46 to "jumpmaster", 47 to "boxing",
        48 to "floor_climbing", 49 to "baseball", 53 to "diving", 56 to "shooting", 58 to "winter_sport",
        59 to "grinding", 62 to "hiit", 63 to "video_gaming", 64 to "racket", 65 to "wheelchair_push_walk",
        66 to "wheelchair_push_run", 67 to "meditation", 68 to "para_sport", 69 to "disc_golf",
        70 to "team_sport", 71 to "cricket", 72 to "rugby", 73 to "hockey", 74 to "lacrosse",
        75 to "volleyball", 76 to "water_tubing", 77 to "wakesurfing", 78 to "water_sport", 79 to "archery",
        80 to "mixed_martial_arts", 81 to "motor_sports", 82 to "snorkeling", 83 to "dance",
        84 to "jump_rope", 85 to "pool_apnea", 86 to "mobility", 87 to "geocaching", 88 to "canoeing",
    )

    /** Manufacturers users are likely to encounter, for display only. */
    val MANUFACTURERS: Map<Int, String> = mapOf(
        1 to "Garmin", 6 to "SRM", 7 to "Quarq", 9 to "Saris", 16 to "Timex", 23 to "Suunto",
        32 to "Wahoo", 41 to "Shimano", 45 to "Xplova", 69 to "Stages", 70 to "Sigma Sport",
        73 to "Wattbike", 86 to "Elite", 89 to "Tacx", 107 to "Magene", 115 to "iGPSPORT",
        255 to "Development", 258 to "Lezyne", 260 to "Zwift", 263 to "Favero", 265 to "Strava",
        267 to "Bryton", 268 to "SRAM", 282 to "The Sufferfest", 289 to "Hammerhead", 294 to "COROS",
        310 to "Decathlon", 339 to "Zepp", 340 to "Peloton", 348 to "Huawei",
    )

    val COURSE_POINT_TYPES: Map<Int, String> = mapOf(
        0 to "generic", 1 to "summit", 2 to "valley", 3 to "water", 4 to "food", 5 to "danger",
        6 to "left", 7 to "right", 8 to "straight", 9 to "first_aid", 10 to "fourth_category",
        11 to "third_category", 12 to "second_category", 13 to "first_category", 14 to "hors_category",
        15 to "sprint", 16 to "left_fork", 17 to "right_fork", 18 to "middle_fork", 19 to "slight_left",
        20 to "sharp_left", 21 to "slight_right", 22 to "sharp_right", 23 to "u_turn",
        24 to "segment_start", 25 to "segment_end", 27 to "campsite", 28 to "aid_station",
        29 to "rest_area", 30 to "general_distance", 31 to "service", 32 to "energy_gel",
        33 to "sports_drink", 34 to "mile_marker", 35 to "checkpoint", 36 to "shelter",
        37 to "meeting_spot", 38 to "overlook", 39 to "toilet", 40 to "shower", 41 to "gear",
        42 to "sharp_curve", 43 to "steep_incline", 44 to "tunnel", 45 to "bridge", 46 to "obstacle",
        47 to "crossing", 48 to "store", 49 to "transition", 50 to "navaid", 51 to "transport",
        52 to "alert", 53 to "info",
    )

    /** Converts a FIT timestamp (seconds since the FIT epoch) to Unix epoch milliseconds. */
    fun toEpochMillis(fitSeconds: Long): Long = (fitSeconds + FIT_EPOCH_OFFSET_S) * 1000L
}
