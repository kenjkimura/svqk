package openapi;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkus.smallrye.openapi.OpenApiFilter;
import java.util.*;
import java.util.regex.Pattern;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.parameters.Parameter;

@OpenApiFilter
@RegisterForReflection
public class PathParamOrderFilter implements OASFilter {
  private static final Pattern PATH_VAR = Pattern.compile("\\{([^}/]+)\\}");

  @Override
  public void filterOpenAPI(OpenAPI openAPI) {
    if (openAPI == null || openAPI.getPaths() == null) return;

    openAPI.getPaths().getPathItems().entrySet().stream()
        .filter(this::hasMultiplePathParams)
        .forEach(this::reorderPathParameters);
  }

  private boolean hasMultiplePathParams(Map.Entry<String, PathItem> entry) {
    return extractPathParamNames(entry.getKey()).size() > 1;
  }

  private void reorderPathParameters(Map.Entry<String, PathItem> entry) {
    PathItem pathItem = entry.getValue();
    List<String> pathParamNames = extractPathParamNames(entry.getKey());

    pathItem
        .getOperations()
        .values()
        .forEach(
            op -> {
              List<Parameter> reordered = orderByPathParamNames(op.getParameters(), pathParamNames);
              op.setParameters(reordered);
            });
  }

  private List<String> extractPathParamNames(String path) {
    return PATH_VAR.matcher(path).results().map(match -> match.group(1)).toList();
  }

  private List<Parameter> orderByPathParamNames(
      List<Parameter> pathParams, List<String> pathParamNames) {
    return pathParamNames.stream()
        .map(name -> findParamBy(name, pathParams))
        .flatMap(Optional::stream)
        .toList();
  }

  private Optional<Parameter> findParamBy(String paramName, List<Parameter> pathParams) {
    return pathParams.stream().filter(param -> param.getName().equals(paramName)).findFirst();
  }

  private List<Parameter> resortParamsBk(List<Parameter> params, List<String> pathParamNames) {
    return pathParamNames.stream()
        .reduce(
            new ArrayList<Parameter>(),
            (result, paramName) -> {
              var oneParam =
                  params.stream().filter(param -> param.getName().equals(paramName)).findFirst();
              result.add(oneParam.get());
              return result;
            },
            (left, right) -> {
              left.addAll(right);
              return left;
            });
  }

  private List<Parameter> reorderBk(List<Parameter> params, List<String> pathParamNames) {
    Map<String, Integer> indexMap = new HashMap<>();
    for (int i = 0; i < pathParamNames.size(); i++) {
      indexMap.put(pathParamNames.get(i), i);
    }

    return params.stream()
        .sorted(
            Comparator.comparingInt(
                param -> indexMap.getOrDefault(param.getName(), Integer.MAX_VALUE)))
        .toList();
  }
}
