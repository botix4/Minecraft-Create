package com.simibubi.create.content.logistics.trains.management;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.content.logistics.trains.entity.Train;
import com.simibubi.create.content.logistics.trains.management.schedule.FilteredDestination;
import com.simibubi.create.content.logistics.trains.management.schedule.Schedule;
import com.simibubi.create.content.logistics.trains.management.schedule.ScheduleDestination;
import com.simibubi.create.content.logistics.trains.management.schedule.ScheduleEntry;
import com.simibubi.create.content.logistics.trains.management.schedule.ScheduleWaitCondition;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

public class ScheduleRuntime {

	enum State {
		PRE_TRANSIT, IN_TRANSIT, POST_TRANSIT
	}

	Train train;
	Schedule schedule;

	boolean paused;
	boolean isAutoSchedule;
	int currentEntry;
	State state;

	static final int INTERVAL = 40;
	int cooldown;
	List<Integer> conditionProgress;
	List<CompoundTag> conditionContext;

	public ScheduleRuntime(Train train) {
		this.train = train;
		reset();
	}

	public void destinationReached() {
		if (state != State.IN_TRANSIT)
			return;
		state = State.POST_TRANSIT;
		conditionProgress.clear();
		if (currentEntry >= schedule.entries.size())
			return;
		List<List<ScheduleWaitCondition>> conditions = schedule.entries.get(currentEntry).conditions;
		for (int i = 0; i < conditions.size(); i++) {
			conditionProgress.add(0);
			conditionContext.add(new CompoundTag());
		}
	}

	public void tick(Level level) {
		if (schedule == null)
			return;
		if (paused)
			return;
		if (train.navigation.destination != null)
			return;
		if (currentEntry >= schedule.entries.size()) {
			currentEntry = 0;
			if (!schedule.cyclic)
				paused = true;
			return;
		}

		if (cooldown-- > 0)
			return;
		if (state == State.IN_TRANSIT)
			return;
		if (state == State.POST_TRANSIT) {
			tickConditions(level);
			return;
		}

		GlobalStation nextStation = findNextStation();
		if (nextStation == null) {
			cooldown = INTERVAL;
			return;
		}
		if (nextStation == train.currentStation) {
			state = State.IN_TRANSIT;
			destinationReached();
			return;
		}

		train.navigation.setDestination(nextStation);
		state = State.IN_TRANSIT;
	}

	public void tickConditions(Level level) {
		List<List<ScheduleWaitCondition>> conditions = schedule.entries.get(currentEntry).conditions;
		for (int i = 0; i < conditions.size(); i++) {
			List<ScheduleWaitCondition> list = conditions.get(i);
			int progress = conditionProgress.get(i);

			if (progress >= list.size()) {
				state = State.PRE_TRANSIT;
				currentEntry++;
				return;
			}

			CompoundTag tag = conditionContext.get(i);
			ScheduleWaitCondition condition = list.get(progress);
			if (condition.tickCompletion(level, train, tag)) {
				conditionContext.set(i, new CompoundTag());
				conditionProgress.set(i, progress + 1);
			}
		}
	}

	public GlobalStation findNextStation() {
		ScheduleEntry entry = schedule.entries.get(currentEntry);
		ScheduleDestination destination = entry.destination;
		if (destination instanceof FilteredDestination filtered) {
			for (GlobalStation globalStation : train.graph.getStations()) {
				if (globalStation.name.equals(filtered.nameFilter))
					return globalStation;
			}
		}
		return null;
	}

	public void setSchedule(Schedule schedule, boolean auto) {
		reset();
		this.schedule = schedule;
		currentEntry = 0;
		paused = false;
		isAutoSchedule = auto;
	}

	public Schedule getSchedule() {
		return schedule;
	}

	public void discardSchedule() {
		reset();
	}

	private void reset() {
		paused = true;
		isAutoSchedule = false;
		currentEntry = 0;
		schedule = null;
		state = State.PRE_TRANSIT;
		conditionProgress = new ArrayList<>();
		conditionContext = new ArrayList<>();
	}

	public CompoundTag write() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("CurrentEntry", currentEntry);
		tag.putBoolean("AutoSchedule", isAutoSchedule);
		tag.putBoolean("Paused", paused);
		if (schedule != null)
			tag.put("Schedule", schedule.write());
		return tag;
	}

	public void read(CompoundTag tag) {
		reset();
		paused = tag.getBoolean("Paused");
		isAutoSchedule = tag.getBoolean("AutoSchedule");
		currentEntry = tag.getInt("CurrentEntry");
		if (tag.contains("Schedule"))
			schedule = Schedule.fromTag(tag.getCompound("Schedule"));
	}

}
