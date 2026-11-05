package app.undertow.core.database

import androidx.room.TypeConverter
import app.undertow.core.common.DateKey
import app.undertow.core.model.Confidence
import app.undertow.core.model.ObservationSource
import app.undertow.core.model.ObservationType
import app.undertow.core.model.Precision

/**
 * Explicit converters rather than Room's implicit enum handling: the stored strings
 * are then a documented part of the schema instead of an implementation detail that
 * a rename could silently break. Instants stay as epoch millis UTC.
 */
class Converters {

    @TypeConverter
    fun dateKeyToString(key: DateKey): String = key.isoDate

    @TypeConverter
    fun stringToDateKey(value: String): DateKey = DateKey(value)

    @TypeConverter
    fun observationTypeToString(value: ObservationType): String = value.name

    @TypeConverter
    fun stringToObservationType(value: String): ObservationType = ObservationType.valueOf(value)

    @TypeConverter
    fun observationSourceToString(value: ObservationSource): String = value.name

    @TypeConverter
    fun stringToObservationSource(value: String): ObservationSource = ObservationSource.valueOf(value)

    @TypeConverter
    fun precisionToString(value: Precision): String = value.name

    @TypeConverter
    fun stringToPrecision(value: String): Precision = Precision.valueOf(value)

    @TypeConverter
    fun confidenceToString(value: Confidence): String = value.name

    @TypeConverter
    fun stringToConfidence(value: String): Confidence = Confidence.valueOf(value)

    @TypeConverter
    fun ingestOutcomeToString(value: IngestOutcome): String = value.name

    @TypeConverter
    fun stringToIngestOutcome(value: String): IngestOutcome = IngestOutcome.valueOf(value)
}
