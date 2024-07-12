package eu.arrowhead.application.skeleton.consumer.data;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.arrowhead.common.dto.shared.ServiceRegistryResponseDTO;

@JsonInclude(Include.NON_NULL)
public class ServiceRegistryListResponseDTO {
	//=================================================================================================
	// members

	private static final long serialVersionUID = 3892383727230105100L;

	private List<ServiceRegistryResponseDTO> data;
	private long count;

	//=================================================================================================
	// methods

	//-------------------------------------------------------------------------------------------------
	public ServiceRegistryListResponseDTO() {
	}

	//-------------------------------------------------------------------------------------------------
	public ServiceRegistryListResponseDTO(final List<ServiceRegistryResponseDTO> data, final long count) {
		this.data = data;
		this.count = count;
	}

	//-------------------------------------------------------------------------------------------------
	public List<ServiceRegistryResponseDTO> getData() {
		return data;
	}

	public long getCount() {
		return count;
	}

	//-------------------------------------------------------------------------------------------------
	public void setData(final List<ServiceRegistryResponseDTO> data) {
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
