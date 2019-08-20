package me.anon.model

import android.content.Context
import android.os.Parcelable
import android.preference.PreferenceManager
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.android.parcel.Parcelize
import me.anon.grow.R
import me.anon.lib.DateRenderer
import me.anon.lib.TempUnit
import me.anon.lib.Unit
import me.anon.lib.helper.TimeHelper
import java.util.*

/**
 * Schedule root object holding list of feeding schedules
 */
@Parcelize
@JsonClass(generateAdapter = true)
class FeedingSchedule(
	val id: String = UUID.randomUUID().toString(),
	var name: String = "",
	var description: String = "",
	@field:Json(name = "schedules") var _schedules: ArrayList<FeedingScheduleDate>
) : Parcelable {
	@field:Transient var schedules = _schedules
		get() {
			field.sortWith(compareBy<FeedingScheduleDate> { it.stageRange[0].ordinal }.thenBy { it.dateRange[0] })
			return field
		}

	constructor() : this(
		id = UUID.randomUUID().toString(),
		name = "",
		description = "",
		_schedules = arrayListOf()
	){}
}

/**
 * Feeding schedule for specific date
 */
@Parcelize
@JsonClass(generateAdapter = true)
class FeedingScheduleDate(
	val id: String = UUID.randomUUID().toString(),
	var dateRange: Array<Int>,
	var stageRange: Array<PlantStage>,
	var additives: ArrayList<Additive> = arrayListOf()
) : Parcelable {
	constructor() : this(
		id = UUID.randomUUID().toString(),
		dateRange = arrayOf(),
		stageRange = arrayOf(),
		additives = arrayListOf()
	){}
}

abstract class Action(
	open var date: Long = System.currentTimeMillis(),
	open var notes: String? = null
) : Parcelable
{
	enum class ActionName private constructor(val printString: Int, val colour: Int)
	{
		FIM(R.string.action_fim, -0x65003380),
		FLUSH(R.string.action_flush, -0x65001f7e),
		FOLIAR_FEED(R.string.action_foliar_feed, -0x65191164),
		LST(R.string.action_lst, -0x65000a63),
		LOLLIPOP(R.string.action_lolipop, -0x65002e80),
		PESTICIDE_APPLICATION(R.string.action_pesticide_application, -0x65106566),
		TOP(R.string.action_topped, -0x6543555c),
		TRANSPLANTED(R.string.action_transplanted, -0x65000073),
		TRIM(R.string.action_trim, -0x6500546f);

		companion object
		{
			@JvmStatic
			public fun names(): IntArray
			{
				val names = IntArray(values().size)
				for (index in names.indices)
				{
					names[index] = values()[index].printString
				}

				return names
			}
		}
	}

	override fun equals(o: Any?): Boolean
	{
		if (o === this) return true
		if (o !is Action) return false
		if (!super.equals(o)) return false
		return this.date == o.date
	}
}

@Parcelize
@JsonClass(generateAdapter = true)
class EmptyAction(
	var action: ActionName? = null,

	override var date: Long = System.currentTimeMillis(),
	override var notes: String? = null
) : Action(date, notes)
{
	public var type: String = "Action"
}

@Parcelize
@JsonClass(generateAdapter = true)
class Garden(
	var name: String = "",
	var plantIds: ArrayList<String> = arrayListOf()
) : Parcelable

@Parcelize
@JsonClass(generateAdapter = true)
class NoteAction(
	override var date: Long = System.currentTimeMillis(),
	override var notes: String? = null
) : Action(date, notes)
{
	public var type: String = "Note"
}

@Parcelize
@JsonClass(generateAdapter = true)
class StageChange(
	var newStage: PlantStage = PlantStage.PLANTED,

	override var date: Long = System.currentTimeMillis(),
	override var notes: String? = null
) : Action(date, notes)
{
	public var type: String = "StageChange"
}

/**
 * Plant model
 */
@Parcelize
@JsonClass(generateAdapter = true)
class Plant(
	var id: String = UUID.randomUUID().toString(),
	var name: String = "",
	var strain: String? = null,
	var plantDate: Long = System.currentTimeMillis(),
	var clone: Boolean = false,
	var medium: PlantMedium = PlantMedium.SOIL,
	var mediumDetails: String? = null,
	var images: ArrayList<String>? = arrayListOf(),
	var actions: ArrayList<Action>? = arrayListOf()
) : Parcelable
{
	public val stage: PlantStage
		get() {
			actions?.let {
				for (index in it.indices.reversed())
				{
					if (it[index] is StageChange)
					{
						return (it[index] as StageChange).newStage
					}
				}
			}

			// Apparently this could be reached
			return PlantStage.PLANTED
		}

	public fun generateShortSummary(context: Context): String
	{
		val measureUnit = Unit.getSelectedMeasurementUnit(context)
		val deliveryUnit = Unit.getSelectedDeliveryUnit(context)

		var summary = ""

		if (stage == PlantStage.HARVESTED)
		{
			summary += context.getString(R.string.harvested)
		}
		else
		{
			val planted = DateRenderer().timeAgo(plantDate.toDouble(), 3)
			summary += "<b>" + planted.time.toInt() + " " + planted.unit.type + "</b>"

			actions?.let { actions ->
				var lastWater: Water? = null

				val actions = actions
				for (index in actions.indices.reversed())
				{
					val action = actions[index]

					if (action.javaClass == Water::class.java && lastWater == null)
					{
						lastWater = action as Water
					}
				}

				val stageTimes = calculateStageTime()

				if (stageTimes.containsKey(stage))
				{
					summary += " / <b>${TimeHelper.toDays(stageTimes[stage] ?: 0).toInt()}${context.getString(stage!!.printString).substring(0, 1).toLowerCase()}</b>"
				}

				if (lastWater != null)
				{
					summary += "<br/>"
					summary += context.getString(R.string.watered_ago, DateRenderer().timeAgo(lastWater.date.toDouble()).formattedDate)
					summary += "<br/>"

					lastWater.ph?.let { ph ->
						summary += "<b>$ph pH</b> "

						lastWater.runoff?.let { runoff ->
							summary += "➙ <b>$runoff} pH</b> "
						}
					}

					lastWater.amount?.let {
						summary += "<b>${Unit.ML.to(deliveryUnit, lastWater.amount!!)}${deliveryUnit.label}</b>"
					}
				}
			}
		}

		if (summary.endsWith("<br/>"))
		{
			summary = summary.substring(0, summary.length - "<br/>".length)
		}

		return summary
	}

	public fun generateLongSummary(context: Context): String
	{
		val measureUnit = Unit.getSelectedMeasurementUnit(context)
		val deliveryUnit = Unit.getSelectedDeliveryUnit(context)

		var summary = ""

		strain?.let {
			summary += "$it - "
		}

		if (stage == PlantStage.HARVESTED)
		{
			summary += context.getString(R.string.harvested)
		}
		else
		{
			val planted = DateRenderer().timeAgo(plantDate.toDouble(), 3)
			summary += "<b>"
			summary += context.getString(R.string.planted_ago, planted.time.toInt().toString() + " " + planted.unit.type)
			summary += "</b>"

			actions?.let { actions ->
				var lastWater: Water? = null

				val actions = actions
				for (index in actions.indices.reversed())
				{
					val action = actions[index]

					if (action.javaClass == Water::class.java && lastWater == null)
					{
						lastWater = action as Water
					}
				}

				val stageTimes = calculateStageTime()

				if (stageTimes.containsKey(stage))
				{
					summary += " / <b>${TimeHelper.toDays(stageTimes[stage] ?: 0).toInt()}${context.getString(stage.printString).substring(0, 1).toLowerCase()}</b>"
				}

				lastWater?.let {
					summary += "<br/><br/>"
					summary += context.getString(R.string.last_watered_ago, DateRenderer().timeAgo(lastWater.date.toDouble()).formattedDate)
					summary += "<br/>"

					lastWater.ph?.let { ph ->
						summary += "<b>$ph pH</b> "

						lastWater.runoff?.let { runoff ->
							summary += "➙ <b>$runoff pH</b> "
						}
					}

					lastWater.amount?.let {
						summary += "<b>${Unit.ML.to(deliveryUnit, lastWater.amount!!)}${deliveryUnit.label}</b>"
					}

					lastWater.additives?.let {
						var total = it.sumByDouble { it.amount ?: 0.0 }
						summary += "<br/> + <b>" + Unit.ML.to(measureUnit, total) + measureUnit.label + "</b> " + context.getString(R.string.additives)
					}
				}
			}
		}

		if (summary.endsWith("<br/>"))
		{
			summary = summary.substring(0, summary.length - "<br/>".length)
		}

		return summary
	}

	/**
	 * Returns a map of plant stages
	 * @return
	 */
	public fun getStages(): LinkedHashMap<PlantStage, Action>
	{
		val stages = LinkedHashMap<PlantStage, Action>()

		actions?.let { actions ->
			for (index in actions.indices.reversed())
			{
				if (actions[index] is StageChange)
				{
					stages[(actions[index] as StageChange).newStage] = actions[index]
				}
			}

			if (stages.isEmpty())
			{
				val stageChange = StageChange(PlantStage.PLANTED)
				stageChange.date = plantDate
				stages[PlantStage.PLANTED] = stageChange
			}
		}

		return stages
	}

	/**
	 * Calculates the time spent in each plant stage
	 *
	 * @return The list of plant stages with time in milliseconds. Keys are in order of stage defined in [PlantStage]
	 */
	public fun calculateStageTime(): SortedMap<PlantStage, Long>
	{
		val startDate = plantDate
		var endDate = System.currentTimeMillis()
		val stages = TreeMap<PlantStage, Long>(Comparator { lhs, rhs ->
			if (lhs.ordinal < rhs.ordinal)
			{
				return@Comparator 1
			}
			else if (lhs.ordinal > rhs.ordinal)
			{
				return@Comparator -1
			}

			0
		})

		actions?.let { actions ->
			for (action in actions)
			{
				if (action is StageChange)
				{
					stages[action.newStage] = action.date

					if (action.newStage == PlantStage.HARVESTED)
					{
						endDate = action.date
					}
				}
			}
		}

		var stageIndex = 0
		var lastStage: Long = 0
		if (!stages.isEmpty())
		{
			var previous = stages.firstKey()
			for (plantStage in stages.keys)
			{
				var difference: Long = 0
				if (stageIndex == 0)
				{
					difference = endDate - (stages[plantStage] ?: 0)
				}
				else
				{
					difference = lastStage - (stages[plantStage] ?: 0)
				}

				previous = plantStage
				lastStage = stages[plantStage] ?: 0
				stageIndex++

				stages[plantStage] = difference
			}
		}
		else
		{
			val planted = PlantStage.PLANTED
			stages[planted] = 0L
		}

		return stages
	}
}

enum class PlantMedium private constructor(val printString: Int)
{
	SOIL(R.string.soil),
	HYDRO(R.string.hydroponics),
	COCO(R.string.coco_coir),
	AERO(R.string.aeroponics);

	companion object
	{
		fun names(context: Context): Array<String>
		{
			val names = arrayListOf<String>()
			values().forEach { medium ->
				names.add(context.getString(medium.printString))
			}

			return names.toTypedArray()
		}
	}
}

enum class PlantStage private constructor(val printString: Int)
{
	PLANTED(R.string.planted),
	GERMINATION(R.string.germination),
	SEEDLING(R.string.seedling),
	CUTTING(R.string.cutting),
	VEGETATION(R.string.vegetation),
	FLOWER(R.string.flowering),
	DRYING(R.string.drying),
	CURING(R.string.curing),
	HARVESTED(R.string.harvested);

	companion object
	{
		public fun names(context: Context): Array<String>
		{
			val names = arrayListOf<String>()
			values().forEach { stage ->
				names.add(context.getString(stage.printString))
			}

			return names.toTypedArray()
		}

		public fun valueOfPrintString(context: Context, printString: String): PlantStage?
		{
			for (plantStage in values())
			{
				if (context.getString(plantStage.printString) == printString)
				{
					return plantStage
				}
			}

			return null
		}
	}
}

@Parcelize
@JsonClass(generateAdapter = true)
class Water(
	var ppm: Double? = null,
	var ph: Double? = null,
	var runoff: Double? = null,
	var amount: Double? = null,
	var temp: Double? = null,
	var additives: ArrayList<Additive> = arrayListOf(),

	override var date: Long = System.currentTimeMillis(),
	override var notes: String? = null
) : Action(date, notes), Parcelable
{
	public var type: String = "Water"

	@Deprecated("")
	public var nutrient: Nutrient? = null
	@Deprecated("")
	public var mlpl: Double? = null

	public fun getSummary(context: Context): String
	{
		val measureUnit = Unit.getSelectedMeasurementUnit(context)
		val deliveryUnit = Unit.getSelectedDeliveryUnit(context)
		val tempUnit = TempUnit.getSelectedTemperatureUnit(context)
		val usingEc = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("tds_ec", false)

		var summary = ""
		var waterStr = StringBuilder()

		ph?.let {
			waterStr.append("<b>")
			waterStr.append(context.getString(R.string.plant_summary_ph))
			waterStr.append("</b>")
			waterStr.append(it)
			waterStr.append(", ")
		}

		runoff?.let {
			waterStr.append("<b>")
			waterStr.append(context.getString(R.string.plant_summary_out_ph))
			waterStr.append("</b>")
			waterStr.append(it)
			waterStr.append(", ")
		}

		summary += if (waterStr.toString().isNotEmpty()) waterStr.toString().substring(0, waterStr.length - 2) + "<br/>" else ""

		waterStr = StringBuilder()

		ppm?.let {
			var ppm = it.toLong().toString()
			if (usingEc)
			{
				waterStr.append("<b>EC: </b>")
				ppm = (it * 2.0 / 1000.0).toString()
			}
			else
			{
				waterStr.append("<b>PPM: </b>")
			}

			waterStr.append(ppm)
			waterStr.append(", ")
		}

		amount?.let {
			waterStr.append("<b>")
			waterStr.append(context.getString(R.string.plant_summary_amount))
			waterStr.append("</b>")
			waterStr.append(Unit.ML.to(deliveryUnit, it))
			waterStr.append(deliveryUnit.label)
			waterStr.append(", ")
		}

		temp?.let {
			waterStr.append("<b>")
			waterStr.append(context.getString(R.string.plant_summary_temp))
			waterStr.append("</b>")
			waterStr.append(TempUnit.CELCIUS.to(tempUnit, it))
			waterStr.append("º").append(tempUnit.label).append(", ")
		}

		summary += if (waterStr.toString().isNotEmpty()) waterStr.toString().substring(0, waterStr.length - 2) + "<br/>" else ""

		waterStr = StringBuilder()

		if (additives.size > 0)
		{
			waterStr.append("<b>")
			waterStr.append(context.getString(R.string.plant_summary_additives))
			waterStr.append("</b>")

			additives.forEach { additive ->
				if (additive.amount == null) return@forEach

				val converted = Unit.ML.to(measureUnit, additive.amount!!)
				val amountStr = if (converted == Math.floor(converted)) converted.toInt().toString() else converted.toString()

				waterStr.append("<br/>&nbsp;&nbsp;&nbsp;&nbsp;• ")
				waterStr.append(additive.description)
				waterStr.append("  -  ")
				waterStr.append(amountStr)
				waterStr.append(measureUnit.label)
				waterStr.append("/")
				waterStr.append(deliveryUnit.label)
			}
		}

		summary += waterStr.toString()

		return summary
	}
}

@Parcelize
@JsonClass(generateAdapter = true)
class Additive(
	var amount: Double? = null,
	var description: String? = null
) : Parcelable
