function bridgeCall(method, params = {}) {
  if (!hasBridge()) {
    return Promise.reject(new Error('Bridge not available'));
  }

  const request = {
    id: uuid(),
    method,
    params
  };

  return new Promise((resolve, reject) => {
    window.cefQuery({
      request: JSON.stringify(request),
      onSuccess: (responseJson) => {
        try {
          const response = JSON.parse(responseJson);
          if (response.error) {
            reject(new Error(response.error));
          } else {
            resolve(response.result ?? null);
          }
        } catch (e) {
          reject(e);
        }
      },
      onFailure: (_, msg) => reject(new Error(msg || 'Bridge failure'))
    });
  });
}
window.bridgeCall = bridgeCall;
