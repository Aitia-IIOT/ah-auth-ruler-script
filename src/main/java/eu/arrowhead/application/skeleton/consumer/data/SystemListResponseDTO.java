package eu.arrowhead.application.skeleton.consumer.data;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.arrowhead.common.dto.shared.SystemResponseDTO;

@JsonInclude(Include.NON_NULL)
public class SystemListResponseDTO {
	//=================================================================================================
	// members

	private List<SystemResponseDTO> data = new ArrayList<>();
	private long count;

	//=================================================================================================
	// methods

	//-------------------------------------------------------------------------------------------------
	public SystemListResponseDTO() {
	}

	//-------------------------------------------------------------------------------------------------
	public SystemListResponseDTO(final List<SystemResponseDTO> systemResponseDTOList, final int totalNumberOfSystems) {
		super();
		this.data = systemResponseDTOList;
		this.count = totalNumberOfSystems;
	}

	//-------------------------------------------------------------------------------------------------
	public List<SystemResponseDTO> getData() {
		return data;
	}

	public long getCount() {
		return count;
	}

	//-------------------------------------------------------------------------------------------------
	public void setData(final List<SystemResponseDTO> data) {
		this.data = data;
	}

	public void setCount(final long count) {
		this.count = count;
	}

	//-------------------------------------------------------------------------------------------------
	@Override
	public String toString() {
		try {
			return new ObjectMapper().writeValueAsString(this);
		} catch (final JsonProcessingException ex) {
			return "toString failure";
		}
	}
}
