package org.mifosplatform.collectionbatch.timeperiod.handler;

import org.mifosplatform.collectionbatch.timeperiod.service.TimePeriodWritePlatformService;
import org.mifosplatform.commands.annotation.CommandType;
import org.mifosplatform.commands.handler.NewCommandSourceHandler;
import org.mifosplatform.infrastructure.core.api.JsonCommand;
import org.mifosplatform.infrastructure.core.data.CommandProcessingResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@CommandType(entity = "TIMEPERIOD", action = "CREATE")
public class CreateTimePeriodCommandHandler implements NewCommandSourceHandler{
	private final TimePeriodWritePlatformService timePeriodWritePlatformService;

	@Autowired
	public CreateTimePeriodCommandHandler(TimePeriodWritePlatformService timePeriodWritePlatformService) {
		this.timePeriodWritePlatformService = timePeriodWritePlatformService;
	}

	@Override
	public CommandProcessingResult processCommand(JsonCommand command) {
		return this.timePeriodWritePlatformService.createTimeperiod(command);
	}
}
